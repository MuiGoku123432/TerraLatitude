#!/usr/bin/env python3
"""Exercise the startup API check against actual supported and unsupported FTF binaries."""
from __future__ import annotations
import argparse
import importlib.util
import json
import os
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('terralatitude_api_build', ROOT / 'build.py')
build = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = build
spec.loader.exec_module(build)
UNSUPPORTED_SHA256 = '0ab7d71948cc9b43037e0f02611fb3be782c84379e034ab7e487fd98a1fc5529'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    build.add_build_arguments(parser)
    parser.add_argument('--unsupported-ftf-jar', type=Path, required=True,
                        help='Original pinned FTF 1.0.0007 JAR without the public raw API')
    args = parser.parse_args()
    report = {'passed': False, 'scope': 'Direct API guard invocation, not mod-loader startup'}
    output = ROOT / 'reports/ftf-api.json'
    output.parent.mkdir(exist_ok=True)
    try:
        if build.sha256(args.unsupported_ftf_jar) != UNSUPPORTED_SHA256:
            raise RuntimeError('Unsupported fixture must be the original recorded FTF 1.0.0007 binary.')
        settings = build.build_settings(args)
        deps, _ = build.dependencies(settings)
        jdk = build.java_home(settings.java_home)
        artifact = ROOT / 'dist' / build.ARTIFACT
        report['addon_sha256'] = build.sha256(artifact)
        report['supported_ftf_sha256'] = build.sha256(deps[3])
        report['unsupported_ftf_sha256'] = UNSUPPORTED_SHA256
        tests = ROOT / 'build/test-classes'
        for label, selected, arguments in [('supported', deps[3], []),
                                          ('unsupported', args.unsupported_ftf_jar, ['missing'])]:
            paths = [*deps]
            paths[3] = selected
            cp = os.pathsep.join(map(str, [artifact, tests, *paths]))
            report[label] = build.run([build.java_tool(jdk, 'java'), '-ea', '-classpath', cp,
                                     'dev.fnf.terralatitude.FtfApiTest', *arguments])
        report['passed'] = True
        print(report['supported'])
        print(report['unsupported'])
        return 0
    except Exception as error:
        report['error'] = str(error)
        print(error, file=sys.stderr)
        return 1
    finally:
        output.write_text(json.dumps(report, indent=2) + '\n')


if __name__ == '__main__':
    raise SystemExit(main())
