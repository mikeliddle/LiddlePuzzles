# Google Play Console checklist

Draft for the first Play listing of `fyi.mllab.puzzle`. These answers describe
the release variant in this branch, not the validation APK or an already
published Play build. Confirm against the actual artifact and live prompts
before submitting.

| Play Console field | Draft |
| --- | --- |
| Main store listing | English (US), Game → Puzzle. Use the [copy and assets](README.md) in this directory. |
| Store contact | `support@mllab.fyi` (public support and privacy contact). |
| Privacy policy | `https://mllab.fyi/liddle-puzzles/privacy`. Publish and verify the live page: accessible without login or geofencing, non-editable HTML, and identifies the listed developer or Liddle Puzzles. Include the privacy contact, local storage and deletion by clearing app data or uninstalling, data handling/security, and the external browser and Sharesheet handoffs. The release variant on this branch links there from Settings → About. |
| Data safety | **No data collected or shared that must be disclosed.** See scope below. |
| Ads / Advertising ID | No ads; no advertising ID use. |
| App access | All functionality available without special access. No account, login, membership, paywall, or reviewer credentials. Daily Word uses the device date. |
| Account deletion | No in-app account creation. Account deletion requirement does not apply; local data can be removed via Android settings or uninstall. |
| Sensitive permissions | None declared in the release APK. |
| Financial features / Health apps | No financial or health features. The local Responsible mode puzzle limit is a gameplay setting, not a health feature. |
| Other prompts if shown | No government affiliation, news, dating, or social features. Check the actual prompts shown for this listing. |
| Target audience and content | **Owner decision:** Select only the age groups the app is designed and marketed for. If the audience includes children, review the [Families requirements](https://support.google.com/googleplay/android-developer/answer/9893335). Do not infer the target audience from the content rating. |

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

Before submitting, confirm the privacy page is live, the owner has selected
target ages, and the live App content and IARC questionnaires are complete.
See [Prepare your app for review](https://support.google.com/googleplay/android-developer/answer/9859455)
and [target audience guidance](https://support.google.com/googleplay/android-developer/answer/9867159).
