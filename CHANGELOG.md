# 0.0.111 (2020-05-13 / 7ce25e2)

## Added

- Added a warning when synchronous fixtures are used, these are not supported

## Fixed

- Make the whole namespace name and surrounding block a click target for toggle

## Changed

- Show original form in failing assertion
- Change select behaviour in column 3 to be more intuitive
- Sort namespaces by name and vars by line number
- Don't delegate to the original cljs.test/report, no need for all that noise in
  the console
- Only show expected/actual sections when the assertion contains the relevant
  keys

# 0.0.106 (2020-05-13 / cafb56e)

## Fixed

- chui-ui: include compiled styles.clj in jar, not garden-based styles.clj

# 0.0.103 (2020-05-13 / 70c2df1)

## Changed

- Don't wait on next tick in between interceptor steps, so as not to unduly slow
  things down
- UI improvements

# 0.0.94 (2020-05-12 / e9fc96b)

## Changed

- Don't call capture-test-data at the top level, this may fix issues when our
  code is included in a doo project

# 0.0.91 (2020-05-12 / 0a80a97)

## Changed

- Improve release process
- Colorize progress bar / top bar based on run result

# 0.0.73 (2020-05-11 / a151fae)

- Initial alpha release of chui-core, chui-ui, and chui-shadow