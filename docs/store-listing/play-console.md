# Google Play Console checklist

Draft for the first Play listing of `fyi.mllab.puzzle`. These answers describe
the release variant in this source tree, not the validation APK or an already
published Play build. Confirm against the submission artifact and live prompts.

| Play Console field | Draft |
| --- | --- |
| Main store listing | English (US), Game → Puzzle. Use the [copy and assets](README.md) in this directory. |
| Store contact | `support@mllab.fyi` (public support and privacy contact). |
| Privacy policy | [Public privacy page](https://mllab.fyi/liddle-puzzles/privacy), also linked from Settings → About. Confirm the page remains publicly accessible and matches the submission artifact. |
| Data safety | **No data collected or shared that must be disclosed.** See scope below. |
| Ads / Advertising ID | No ads; no advertising ID use. |
| App access | All functionality available without special access. No account, login, membership, paywall, or reviewer credentials. Daily Word uses the device date. |
| Account deletion | No in-app account creation. Account deletion requirement does not apply; local data can be removed via Android settings or uninstall. |
| Sensitive permissions | None declared in the release APK. |
| Financial features / Health apps | No financial or health features. The local Responsible mode puzzle limit is a gameplay setting, not a health feature. |
| Other prompts if shown | No government affiliation, news, dating, or social features. Check the actual prompts shown for this listing. |
| Target audience and content | **Owner confirmed: Ages 9–12.** Older bands are not confirmed; do not infer them from the content rating. [Families requirements](https://support.google.com/googleplay/android-developer/answer/9893335) apply; see the submission hold below. |

## Data safety scope

The locally built unsigned release APK declares no permissions (including
`INTERNET` and `AD_ID`) and sets `allowBackup=false`; the release runtime
classpath lists no dependencies. The source has no accounts, ads, analytics,
crash reporting, or backend. Settings, puzzle state, entries, history and
times are app-private on-device data. Android cloud backup is disabled;
device-maker transfers can behave independently.

Share is an explicit player action: the app hands a starting puzzle link to
the Android Sharesheet, and the player selects the receiving app. The payload
does not contain moves, guesses, notes, history, solve times or answers.
Google's [Data safety guidance](https://support.google.com/googleplay/android-developer/answer/10787469)
excludes locally processed data and exempts expected, specifically
user-initiated third-party transfers from sharing disclosure. About links open
fixed URLs in a separate browser without attaching app data. Apply the declaration
to all versions actually distributed on Play under this package.

## Families submission hold: Daily Word

Google Play requires content accessible to children to be appropriate for
them. Do not submit the current artifact as Families-ready. A host check
loaded the unsigned release APK's two distinct word lists into the game
engine: `answers-v1.txt` (619 scheduled answers, chosen by date and revealed
after a loss) and `guesses-v1.txt` (12,653 accepted entries). The probes
`cunts`, `fucks`, and `shits` were absent from the answers but accepted and
retained as guesses. This was a spot check, not a full answer-list review.
The board renders submitted guesses, and its accessible rows expose their
letters. Strong language can therefore appear during ordinary play when a
player types an accepted word, even if it is never a scheduled answer.

Before submitting for ages 9–12:

1. Review **both** lists from the exact submission artifact for age
   suitability, including profanity, slurs and sexual terms. Check the
   answer after six misses separately from the allowed-guess behavior.
2. Resolve how the app handles strong language for children, then verify on
   an isolated test install: try representative terms as drafts and
   submitted guesses, check what stays visible after reopening, and check
   what large controls and accessibility output expose. Check the answer
   reveal and a shared-date replay as well. This listing change does not
   modify the word lists or gameplay.
3. Review the remaining Families requirements for the actual artifact and
   listing, including child data practices and the external Sharesheet
   handoff. No Families compliance determination has been made.

## IARC content rating

Choose **Game** and enter a monitored email for IARC correspondence. Answer
the [live questionnaire](https://support.google.com/googleplay/android-developer/answer/9859655)
with these facts, not a guessed rating:

- Eight abstract grid and word puzzles. Minesweeper uses stylized mines and
  non-graphic feedback, without characters, injury, or blood.
- Daily Word accepts some player-typed profanity and sexual terms from its
  bundled guess dictionary and can display those guesses locally. It does
  not publicly display or share them. Do not blanket-answer “no language”
  without checking the wording of the question.
- No in-app chat, community, public user posts, location, purchases, gambling,
  or randomized paid items. Players can use Android's Sharesheet to send a
  predefined puzzle through an external app. About links open an external
  browser; there is no in-app web browser.

In Play Console, enter the listing assets, support email and public privacy
URL; confirm all intended age bands; complete Data safety, the other live
App content declarations, and IARC using the final build's behavior.
See [Prepare your app for review](https://support.google.com/googleplay/android-developer/answer/9859455)
and [target audience guidance](https://support.google.com/googleplay/android-developer/answer/9867159).
