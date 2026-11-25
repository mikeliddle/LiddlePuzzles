# Privacy

Effective date: November 20, 2026.

The current Android application ID is `fyi.mllab.puzzle`. It has storage separate
from the older `com.quietgrid.sudoku` app; old private saves and settings are not
automatically transferred or read for migration.

Liddle Puzzles is an offline app. It does not collect or transmit personal data,
usage data, device identifiers, or crash reports to the developer or any
third party. It has no accounts, advertising, analytics, or network permission.
The links in Settings > About open in an external browser. The browser and
the site handle those requests under their own privacy policies.

The app stores your current puzzle, entries, notes, move history, active solve
time, personal-best times, and appearance/gameplay preferences locally in
app-private storage. These are used only to provide the game on your device.
Daily Word also stores each played local calendar date, submitted guesses,
unfinished draft, and active-play elapsed time in a separate app-private file. Changing dates or
switching puzzles does not reset those records. The daily answer is computed
from bundled data and your device's local date; there is no server check-in.
Takuzu, Nonograms, Futoshiki, Minesweeper, Numberlink and Slitherlink each store
their own generated puzzle, progress, and active-play elapsed time in separate private files. Minesweeper
also saves its local random seed and revealed/flagged squares; the path games save
drawn edges. Switching games does not replace another game's progress.
Takuzu, Nonograms, Futoshiki, Numberlink and Slitherlink also keep up to 500
entry/edge states in the same private save so move history survives restarts.
The app disables Android cloud backup. Some devices may apply their own
device-transfer behavior; the app itself does not upload or synchronize data.

New Sudoku puzzles are generated locally. There is no puzzle-rating, curated
collection or progress import/export feature. Older installations may retain private
collection and classification files; this version leaves them untouched and
does not read, write or use them. A previously imported active puzzle remains
resumable from the ordinary current-game save.

Where puzzle sharing is available, choosing Share opens the Android Sharesheet
with a link containing only the puzzle's starting definition. For completed
puzzles with a recorded solve time, the outgoing message also shows that time
as text outside the link. Moves, notes, answers and history are not sent.
The app you choose to send the message handles it under its own privacy policy.
A recipient's shared-puzzle progress is stored separately on their device.

For HTTPS puzzle links, the puzzle definition is in the URL fragment, which
is not included in the browser's HTTP request to the fallback page. The
browser, site host, and Android's link-verification service may handle web
requests independently of the app.

Clearing app storage or uninstalling removes the locally stored app data.
Installing a compatible update with the same signing certificate preserves it.

Downloading the APK from a website is separate from using the app. That
website may process download requests under its own privacy policy. Android,
your device manufacturer, and your installer may independently process data
under their own policies; this app does not control those services.
