"""Load launch preflight/postcheck only; no load generation, login or DB writes.

Credentials arrive only through process environment. Output never includes them.
This is Task 1 correctness/evidence checking, not Task 2 performance analysis.
"""
import argparse
import collections
import csv
import datetime as dt
import hashlib
import hmac
import json
import os
from pathlib import Path
import re
import sqlite3
import urllib.request

EXPECTED = {
    'test-plans/23120274_Load_20261118.jmx': '3b63a24f3e36b469fb3eca4b983019eb69b462a9feea7e7f2ec9bc910ecca261',
    'scripts/category-load-support.groovy': 'fc05672162cf5a28df0a35a0e9b6869e10811e74d27a8022a7dd43be6224f995',
    'scripts/recover-category-load.groovy': '6849ef3de5437900083300def23c44b7e0776b45fc5e0c7bd83db0c892e962ec',
    'data/categories.csv': 'c237523a3f44a5d1471b3d4483bcb25c76495e9972e6a0f2b6bf9bec0dc1a23e',
}
LABELS = ['01_Admin_Login', '02_View_Categories', '03_Create_Category',
          '04_Delete_Category', '05_Verify_Category_Deletion']
CHAIN = ['CREATE_INTENT', 'CREATE_CONFIRMED', 'DELETE_INTENT',
         'DELETE_ACKNOWLEDGED', 'ABSENCE_VERIFIED']


def now():
    return dt.datetime.now(dt.timezone.utc).isoformat()


def require(condition, code):
    if not condition:
        raise RuntimeError(code)


def get(endpoint):
    with urllib.request.urlopen('http://localhost:3000' + endpoint, timeout=10) as response:
        require(response.status == 200, 'BASELINE_HTTP_STATUS')
        data = json.load(response)
        require(isinstance(data, list), 'BASELINE_NOT_ARRAY')
        return data


def readonly_admin(root, verify_password):
    email, password = os.environ.get('ESHOP_ADMIN_EMAIL'), os.environ.get('ESHOP_ADMIN_PASSWORD')
    require(bool(email) and bool(password), 'MISSING_CREDENTIAL_ENV')
    database_uri = (root / 'eshop-sut/backend/database.sqlite').resolve().as_uri() + '?mode=ro'
    connection = sqlite3.connect(database_uri, uri=True)
    connection.row_factory = sqlite3.Row
    try:
        users = connection.execute(
            'SELECT id,role,password,login_attempts,locked_until FROM users WHERE email=?', (email,)
        ).fetchall()
        require(len(users) == 1 and users[0]['role'] == 'admin', 'ADMIN_NOT_UNIQUE_OR_WRONG_ROLE')
        user = users[0]
        if verify_password:
            require(isinstance(user['password'], str) and
                    hmac.compare_digest(user['password'].encode(), password.encode()),
                    'PASSWORD_MISMATCH_NO_LOGIN_ATTEMPTED')
        require(user['login_attempts'] == 0 and user['locked_until'] is None,
                'ADMIN_NOT_AT_CLEAN_UNLOCKED_BASELINE')
        return {key: user[key] for key in ('id', 'role', 'login_attempts', 'locked_until')}
    finally:
        connection.close()


def canonical(rows):
    return sorted(rows, key=lambda item: item['id'])


def snapshot(root):
    return {'categories': get('/api/categories'), 'products': get('/api/products'),
            'admin': readonly_admin(root, True)}


def fingerprint(root):
    paths = list(EXPECTED) + ['eshop-sut/backend/server.js', 'eshop-sut/backend/database.js',
                              'scripts/run-category-load.ps1', 'scripts/check-category-load-run.py']
    return {name: hashlib.sha256((root / name).read_bytes()).hexdigest() for name in paths}


def preflight(root):
    hashes = fingerprint(root)
    require(all(hashes[name] == digest for name, digest in EXPECTED.items()), 'REVIEWED_HASH_CHANGED')
    admin = readonly_admin(root, True)  # Check without a failed login/lockout side effect.
    baseline = snapshot(root)
    require(baseline['admin'] == admin, 'ADMIN_CHANGED_DURING_PREFLIGHT')
    return {'kind': 'load_preflight', 'checkedAt': now(), 'status': 'READY',
            'hashesBefore': hashes, 'baseline': baseline,
            'credentialsVerifiedReadOnly': True, 'credentialValuesOmitted': True}


def write_new(path, data):
    with path.open('x', encoding='utf-8') as stream:
        json.dump(data, stream, ensure_ascii=False, indent=2)
        stream.write('\n')


def postcheck(root, run_dir):
    pre = json.loads((run_dir / 'preflight.json').read_text(encoding='utf-8-sig'))
    execution = json.loads((run_dir / 'execution.json').read_text(encoding='utf-8-sig'))
    result = {'kind': 'load_task1_postcheck', 'checkedAt': now(), 'runId': execution['runId'],
              'status': 'REVIEW_REQUIRED', 'checks': {}, 'limitations': [
                  'No SLA pass, capacity estimate, bottleneck diagnosis or Task 2 analysis.',
                  'A passing postcheck does not test forced-stop/timeout recovery.',
                  'Secret scan covers known credentials, JWT patterns and JTL columns; it is not universal.',
              ]}
    checks = result['checks']
    issues = []
    try:
        checks['hashesUnchanged'] = fingerprint(root) == pre['hashesBefore']
        after = snapshot(root)
        write_new(run_dir / 'post-run-baseline.json', {'checkedAt': now(), **after})
        checks['categoriesUnchanged'] = canonical(after['categories']) == canonical(pre['baseline']['categories'])
        checks['productsUnchanged'] = canonical(after['products']) == canonical(pre['baseline']['products'])
        checks['adminUnchangedAndClean'] = after['admin'] == pre['baseline']['admin']
        checks['nativeExitZero'] = execution.get('nativeLauncherExitCode') == 0
        checks['backendProcessUnchanged'] = bool(execution.get('backendProcessUnchanged'))
        checks['resourceMonitorCompleted'] = (execution.get('monitorJobState') == 'Completed' and
                                               execution.get('monitorErrorCount') == 0)
        with (run_dir / 'backend-resources.csv').open(encoding='utf-8-sig', newline='') as stream:
            resource_rows = list(csv.DictReader(stream))
        checks['backendResourceEvidencePresent'] = len(resource_rows) >= 2 and all(
            row.get('sameBackendProcess', '').lower() == 'true' for row in resource_rows)
        checks['noBackendRestartAlert'] = not (run_dir / 'monitor-alert.json').exists()
        result['resourceSampleCount'] = len(resource_rows)
        jtl = run_dir / 'samples.jtl'
        raw = jtl.read_bytes()
        result['rawJtlSHA256'] = hashlib.sha256(raw).hexdigest()
        with jtl.open(encoding='utf-8-sig', newline='') as stream:
            reader = csv.DictReader(stream)
            rows, columns = list(reader), reader.fieldnames or []
        result['jtlColumns'] = columns
        result['samples'] = len(rows)
        result['byLabel'] = dict(collections.Counter(row['label'] for row in rows))
        result['failedSamples'] = sum(row['success'].lower() != 'true' for row in rows)
        checks['fiveHttpLabelsPresent'] = set(result['byLabel']) == set(LABELS)
        checks['allHttpAndAssertionsSuccessful'] = bool(rows) and all(
            row['success'].lower() == 'true' and row['responseCode'] == '200' for row in rows)
        forbidden = {'responseData', 'samplerData', 'requestHeaders', 'responseHeaders', 'password', 'token'}
        checks['noSensitiveJtlColumns'] = not bool(set(columns) & forbidden)
        manifest = root / 'audit/manifests' / execution['runId'] / 'load_local'
        groups = collections.defaultdict(list)
        for file in manifest.glob('thread-*.jsonl'):
            for line in file.read_text(encoding='utf-8').splitlines():
                event = json.loads(line)
                groups[event['intentId']].append(event)
        complete = 0
        correlation_ok = True
        for events in groups.values():
            stages = [item['stepName'] for item in events]
            if stages == CHAIN:
                complete += 1
            names = {item['categoryName'] for item in events}
            ids = {item['actualId'] for item in events if 'actualId' in item}
            correlation_ok &= len(names) == 1 and len(ids) <= 1 and all(
                isinstance(value, int) and value > 0 for value in ids)
        result['ownedIntents'] = len(groups)
        result['completeWorkloadIterations'] = complete
        checks['atLeastOneCompleteIteration'] = complete > 0
        checks['manifestCorrelationConsistent'] = correlation_ok
        recovery = [json.loads(line) for line in (manifest / 'recovery.jsonl').read_text(encoding='utf-8').splitlines()]
        cleanup = [item for item in recovery if item['stepName'] == 'RECOVERY_RESULT'][-1]
        summary = [item for item in recovery if item['stepName'] == 'RUN_SUMMARY'][-1]
        result['cleanupResult'] = cleanup
        result['runSummary'] = summary
        checks['cleanupVerified'] = cleanup['cleanupStatus'] == 'VERIFIED' and not cleanup['unresolvedIntents']
        checks['scheduleNotStoppedByGuard'] = summary['performanceStatus'] == 'SCHEDULE_FINISHED_NOT_A_PERFORMANCE_PASS'
        checks['dashboardExists'] = (run_dir / 'html/index.html').exists()
        all_findings = []
        scan_files = [file for file in run_dir.rglob('*') if file.is_file()]
        scan_files += [file for file in manifest.rglob('*') if file.is_file() and file.suffix in ('.json', '.jsonl')]
        for file in scan_files:
            data = file.read_bytes()
            for key in ('ESHOP_ADMIN_EMAIL', 'ESHOP_ADMIN_PASSWORD'):
                value = os.environ.get(key, '').encode()
                if value and value in data:
                    all_findings.append({'path': str(file.relative_to(root)), 'kind': 'credential literal'})
            if re.search(rb'eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+', data):
                all_findings.append({'path': str(file.relative_to(root)), 'kind': 'JWT pattern'})
        result['secretScan'] = {'filesScanned': len(scan_files), 'findings': all_findings}
        checks['noKnownSecretsDetected'] = not all_findings
    except Exception:
        issues.append('Postcheck incomplete: inspect preserved raw files and local API/manifest state; no reset or broad deletion was attempted.')
    result['issues'] = issues
    if checks and all(checks.values()) and not issues:
        result['status'] = 'FUNCTIONAL_AND_CLEANUP_VERIFIED_NOT_SLA_PASS'
    write_new(run_dir / 'postcheck.json', result)
    return {'status': result['status'], 'checks': checks, 'samples': result.get('samples'),
            'failedSamples': result.get('failedSamples'),
            'completeWorkloadIterations': result.get('completeWorkloadIterations'), 'issues': issues}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('mode', choices=('preflight', 'postcheck'))
    parser.add_argument('--run-dir', type=Path)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    try:
        if args.mode == 'preflight':
            result = preflight(root)
        else:
            require(args.run_dir is not None, 'RUN_DIRECTORY_REQUIRED')
            run_dir = args.run_dir.resolve()
            require(run_dir.is_relative_to(root / 'results/load'), 'RUN_PATH_OUTSIDE_OFFICIAL_LOAD_DIRECTORY')
            result = postcheck(root, run_dir)
        print(json.dumps(result, ensure_ascii=True))
        return 0 if result['status'] in ('READY', 'FUNCTIONAL_AND_CLEANUP_VERIFIED_NOT_SLA_PASS') else 2
    except Exception:
        # Never emit credential-bearing exception context or a raw Login response.
        print(json.dumps({'status': 'BLOCKED', 'reason': 'Preflight/postcheck failed; check backend, clean admin, credentials, hashes and baseline. No login or DB write was attempted by this checker.'}))
        return 2


if __name__ == '__main__':
    raise SystemExit(main())
