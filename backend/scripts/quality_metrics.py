"""Read generated reports only. Run from backend; optionally pass a snapshot directory."""
import json
import re
import sys
from pathlib import Path
import xml.etree.ElementTree as ET

directory = Path(sys.argv[1]) if len(sys.argv) > 1 else Path('target')
jacoco = ET.parse(directory / ('jacoco.xml' if len(sys.argv) > 1 else 'site/jacoco/jacoco.xml')).getroot()
coverage = {}
for counter in jacoco.findall('counter'):
    if counter.attrib['type'] in ('LINE', 'BRANCH'):
        missed, covered = int(counter.attrib['missed']), int(counter.attrib['covered'])
        coverage[counter.attrib['type']] = {
            'covered': covered, 'total': covered + missed,
            'percent': round(100 * covered / (covered + missed), 2),
        }
pmd = ET.parse(directory / 'pmd.xml').getroot()
ns = {'p': 'http://pmd.sourceforge.net/report/2.0.0'}
methods = []
other_findings = []
for file in pmd.findall('p:file', ns):
    for violation in file.findall('p:violation', ns):
        value = re.search(r'cyclomatic complexity of (\d+)', violation.text or '')
        if violation.attrib['rule'] == 'CyclomaticComplexity' and value:
            methods.append({'file': Path(file.attrib['name']).name,
                            'method': violation.attrib.get('method'), 'complexity': int(value[1])})
        else:
            other_findings.append({'file': Path(file.attrib['name']).name,
                                   'rule': violation.attrib['rule'], 'message': violation.text.strip()})
cpd = ET.parse(directory / 'cpd.xml').getroot()
clones = cpd.findall('{*}duplication')
print(json.dumps({'coverage': coverage, 'pmdVersion': pmd.attrib['version'],
                  'javaComplexity': {'methodsAndConstructors': len(methods),
                                     'total': sum(method['complexity'] for method in methods),
                                     'max': max(method['complexity'] for method in methods)},
                  'hotspots': sorted(methods, key=lambda method: -method['complexity'])[:6],
                  'otherPmdFindings': other_findings,
                  'cpd': {'minimumTokens': 100, 'groups': len(clones),
                          'duplicatedLineOccurrences': sum(int(clone.attrib['lines']) *
                                                           len(clone.findall('{*}file')) for clone in clones)}}, indent=2))
