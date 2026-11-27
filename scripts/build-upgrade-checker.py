#!/usr/bin/env python3
"""Bounded public-dependency retries; never retries a compilation/test failure or changes versions."""
import os
import random
import re
import subprocess
import sys
import time

TASKS = [':upgrade-ui-check:assembleDebug', ':upgrade-ui-check:assembleDebugAndroidTest']
BASE = ['./gradlew', '--no-daemon', '--no-parallel', '--max-workers=1', '--no-configuration-cache',
        '-I', 'scripts/upgrade-repositories.init.gradle', '-PupgradeUiCheck=true']


def transient_download_failure(text):
    if re.search(r'(?:^e: |Compilation error|There were failing tests)', text, re.M):
        return False
    return bool(re.search(r'(?:Received status code (?:429|502|503|504)\b|HTTP (?:429|502|503|504)\b|Read timed out|Connection timed out|Temporary failure in name resolution)', text, re.I))


def build(run=subprocess.run, sleep=time.sleep, jitter=random.uniform):
    # Gradle retains successful downloads between attempts; do not use --refresh-dependencies.
    for attempt in range(4):
        result = run(BASE+TASKS, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
        print(result.stdout or '', end='', flush=True)
        if result.returncode == 0:
            break
        if attempt == 3 or not transient_download_failure(result.stdout or ''):
            return result.returncode
        delay = (30 * 2**attempt) + jitter(0, 10)
        print(f'Transient dependency download failure; retry {attempt+2}/4 after {delay:.0f}s, with one worker.', flush=True)
        sleep(delay)
    # Recompile from a clean checker output using only the verified local dependency cache.
    # Offline failure is final: never hide a missing cache entry behind an online retry.
    result = run(BASE+['--offline', ':upgrade-ui-check:clean']+TASKS)
    return result.returncode


if __name__ == '__main__':
    sys.exit(build())
