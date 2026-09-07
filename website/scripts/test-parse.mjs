// Smoke test for the changelog parser. Run with `npm run test:parse`.
import { parseChangelog } from '../src/lib/changelog.ts';

const sample = `# Changelog

## [Unreleased]

## [0.3.1] - 2026-09-07

### Changed

- Item one.
- Item two.

## [0.3.0] - 2026-09-07

### Changed

- Project renamed.
`;

const releases = parseChangelog(sample);
if (releases.length !== 3) {
  console.error('expected 3 releases, got', releases.length);
  process.exit(1);
}
if (releases[0].isUnreleased !== true) {
  console.error('first release should be unreleased');
  process.exit(1);
}
if (releases[1].version !== '0.3.1') {
  console.error('version mismatch');
  process.exit(1);
}
if (!releases[1].isLatest) {
  console.error('0.3.1 should be latest');
  process.exit(1);
}
if (releases[1].sections[0].bullets.length !== 2) {
  console.error('expected 2 bullets');
  process.exit(1);
}
console.log('changelog parser OK');
