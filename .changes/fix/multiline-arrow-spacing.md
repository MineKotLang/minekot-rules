Do not require horizontal whitespace after lambda or `when` arrows that end a line. Arrow corrections now edit only same-line gaps, preventing the quick-fix from inserting trailing whitespace and turning itself into a second diagnostic.

Add focused single-line and multiline correction tests plus the reported MineKot API `Selection.kt` source as a permanent regression fixture.
