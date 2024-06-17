# Changelog

## [Unreleased]

### Added

### Changed

### Fixed

## 2.16.0

### Fixed

- Ensure compatibility with IntelliJ 2024.2 EAP

## 2.15.4

### Fixed
- Fix "Slow operations are prohibited on EDT" warning when reading token from password safe introduced with 2024.1 (
  again)

## 2.15.3

### Fixed
- Fix "Slow operations are prohibited on EDT" warning when reading token from password safe introduced with 2024.1

## 2.15.2

### Fixed
- Fix "OLD_EDT" warning introduced with 2024.1 EAP

## 2.15.1

### Fixed
- Updated all occurrences of deprecated code usage

## 2.15.0

### Changed
- IntelliJ 2023+ is now required

### Fixed
- "Thread context already set error" when showing the token dialog.

## 2.14.2

### Fixed
- Do not ask for token again until an update is triggered by the user or the project has reopened.

## 2.14.1

### Added
- The "What's new" section will now be properly filled.

### Fixed
- Connection errors were being shown even when disabled.
- Login errors were not properly detected so the plugin didn't ask for access tokens.

### Changed
- Connection errors will now be shown if the user triggered a reload, even when they were already shown before.

## 2.14.0

### Added
- Only the first connection error after a successful connection will be reported. This should prevent notification
  spamming when your internet connection is down or VPN is disconnected. See #64

## 2.13.2

### Fixed
- "AlreadyDisposedException" thrown sometimes (somewhere else).

## 2.13.1

### Fixed
- "AlreadyDisposedException" thrown sometimes.

## 2.13.0

### Fixed
- UI freezes while loading pipeline results have been fixed.

## 2.12.0

### Added
- Option to run updates in background, i.e. without showing a progress bar.
- Copy pipeline / merge request URL to clipboard via context menu.

## 2.11.0

### Added
- Mark pipelines that ran successfully but with warning. This uses an undocumented value in the pipeline state and may stop working at any time.

### Fixed
- Fix persistance of hosts for which to always monitor pipelines.

## 2.10.0

### Added
- Option to automatically monitor all projects for a given host.

## 2.9.2

### Fixed
- Fix layout issue with horizontal scrolling bar being show when not needed.

## 2.9.1

### Fixed
- Show correct number of open notifications in banner.

## 2.9.0

### Added
- The plugin shows a notification if an unmapped remote is found. If this notification is ignored long enough IntelliJ will send it to the background and add an entry to the Event Log.\
  This could easily be overseen and result in users being confused why no pipelines are shown. Now a banner will be shown in the toolbar window and allow to open the dialog.

## 2.8.0

### Added
- The values for watched / ignored branches now support wildcards. For example if you want to show pipelines for all release branches watch "release/*".
- You can ignore pipelines that are older than x days.
- You can ignore pipelines for branches which don't exist on the remote (anymore).

## 2.7.0

### Added
- Option to only show pipelines for the latest n tags.

### Changed
- New changelog format which supports the display of latest changes in the plugins menu and on the Jetbrains plugin page.

## 2.6.6

### Fixed
- In 2.6.5 I added a check of the gitlab version to determine if a certain parameter is supported. Unfortunately I wasn't aware that the /api/version endpoint is protected and needs authentication even if you can access other API endpoints
  without authentication. That means that for self hosted gitlab instances which don't require an access token to request a project's pipelines the version could not be determined and this was interpreted as the parameter not being
  supported. I switched to an approach that's either hacky pragmatic - I just make a test query using the parameter and if gitlab tells me that parameter isn't supported I save this information. Long story short: MRs for pipelines should be
  shown again.

### Changed
- New changelog format which supports the display of latest changes in the plugins menu and on the Jetbrains plugin page.

## 2.6.5

### Fixed
- Fix NPE when accessing Gitlab instances older than v14.2. These will not support merge request pipelines. See [#28](https://gitlab.com/ppiag/intellij_gitlab_pipeline_monitor/-/issues/28)
  and [#32](https://gitlab.com/ppiag/intellij_gitlab_pipeline_monitor/-/issues/32)
- Don't show notifications for previous pipelines (either when monitoring a new project or when starting IntelliJ with a project already monitored).

## 2.6.4

### Changed
- Report connection errors with info log level (so they aren't shown with the blinky red icon).

## 2.6.3

### Fixed
- Fix ClassCastException (due a weird bug in the IntelliJ code).

## 2.6.2

### Fixed
- Fix rare NPE when trying to read tags.

## 2.6.1

### Added
- The MR column entry will now link to the MR of an MR pipeline.

### Fixed
- Prevent NPE in PipelineFilter.

### Changed
- Remove usages of deprecated IntelliJ API.

## 2.6.0

### Added
- Support for [merge request pipelines](https://docs.gitlab.com/ee/ci/pipelines/merge_request_pipelines.html).

### Changed
- Update build plugins.

## 2.5.0

### Added
- Ensured compatibility with IntelliJ 2021.3. Due to a change in the notification API you will need to reconfigure the display type of the different status notifications (e.g. disable popups for running pipelines).

## 2.4.1

### Fixed
- Hopefully prevent AlreadyDisposedException when projects are being closed and the plugin tries to execute an update in the background.

## 2.4.0

### Added
- Check if CI is actually enabled for a remote before asking if it should be monitored. This is done using a "best guess" of gitlab host, project path and access token.

## 2.3.0

### Added
- Support for personal and project access tokens. Personal access tokens work for all projects you're part of, project access tokens only for specific projects. The former is the default. Existing tokens are still saved per remote (i.e.
  considered project access tokens) but the next time you enter an access token you can select it to be a personal access token and it will be used for all your future projects on this host. You're still able to use project tokes for
  specific projects, even on hosts for which you already stored a personal access token.
- Instead of a modal dialog now a sticky balloon popup is displayed. Only when you click the contained action will the dialog show up. This should make opening multiple projects with untracked remotes easier.

### Fixed
- Fixed a bug where the project path determined from an HTTPS remote got truncated.
- Whenever an untracked HTTPS remote is found the list of already existing mappings is searched to see if one exists which matches the remote. That should make parsing of remotes more robust as for every host you only (may) have to fix the
  parsed values.

## 2.2.1

### Fixed
- There's a bug in IntelliJ that causes progress bars to not close properly when the underlying task is finished too quickly. I thought I had found a way to work around it but sometimes it still happens. The bug is resolved in IntelliJ
  2021.2 but Jetbrains has told me they won't backport it to 2020.3.

## 2.2.0

### Fixed
- Prevent "already disposed" error when quitting IntelliJ.
- Prevent freezing of UI while trying to reach slow or unavailable gitlab host.
- Fix settings dialog being shown as dirty without any changes made.

### Changed
- Extend debug logging.
- Make gitlab host detection more robust.
- Add validation and tooltips to dialog for unmapped remotes.
- Make gitlab connect timeout configurable (5 seconds by default).
- Reduce logging level of expected errors to info.

## 2.1.1

### Fixed
- Ignoring remotes until next restart didn't always work.

## 2.1.0

### Added
- Show latest open merge request for a branch or allow to create a new one.
- Option to switch display of pipelines between URL, ID and icon.
- Added a small manual: https://gitlab.com/ppiag/intellij\_gitlab\_pipeline\_monitor/-/blob/master/manual.md.

### Fixed
- Don't show untracked remotes popup when one is already open.
- The dialog for untracked remotes will not display the determined gitlab host and project path. When choosing to monitor the remote's project the data will be verified instantly.
- Choosing to be asked again will ask the next time the plugin is loaded or a refresh is triggered manually (instead of asking every 15 seconds).

## 2.0.9

### Fixed
- Continuous bug fixes.

## 2.0.2

### Changed
- Only remotes may be tracked where the UI is reachable under the remote's host. This will be changed in a future release.

## 2.0.1

### Fixed
- Unfortunately due to a bug in IntelliJ with the progress indicator I released 2.0.0 with a last-minute update which caused a serious issue with a modal window freezing. This should be fixed for now but some progress indicators may be left
  dangling. I'm working on it. Sorry for the botched release :-/

## 2.0.0

### Added
- Drastically changed workflow. The plugin will recognize all remotes used in an IntelliJ project and ask which should be monitored. It will then automatically retrieve all necessary data from gitlab. This a) reduces how much you need to
  configure and b) allows to decouple IntelliJ projects from Gitlab projects.
- Use gitlab default target branch for merge requests if none is configured.
- Option to show pipelines for tags.
- Display times in local time zone.
- Add action to open last pipeline in browser (located in Git main menu group).
- Add option to define custom command for opening URLs.

### Fixed
- Don't remove path of configured gitlab URL.

### Changed
- Changed minimum IntelliJ version to 2020.1.

## 1.28.0

### Added
- Ensure compatibility with new Gitlab API (ignore unknown values).

## 1.27.0

### Fixed
- Fix exception when copying commit hash to clipboard.
- (Hopefully) fix exception when sorting branches without update time.

## 1.26.0

### Added
- Add option to disable notifications for connection errors to gitlab<./li>

## 1.25.0

### Added
- Only show the newest 3 notifications, don't spam the older ones.

### Changed
- Improve compatibility with older IntelliJ 2019.3 versions.

## 1.24.0

### Fixed
- Fix NPE when loading git repository.

## 1.23.0

### Added
- Add button to copy current git hash to clipboard.
- Load and show pipelines on startup.

### Fixed
- Fix watched branches not being shown in form.

## 1.22.0

### Fixed
- Fix exception when running plugin on Mac OS X.

## 1.21.0

### Fixed
- Remove dependency on Apache Commons to fix compatibility with PyCharm.

## 1.20.0

### Added
- Checkout branch from context menu.

## 1.19.0

### Changed
- Set compatibility to require at least IntelliJ 2019.3.

## 1.18.0

### Changed
- Add compatibility with IntelliJ 2020.1.

## 1.17.0

### Added
- Add option to disable notifications for watched branches.

## 1.16.0

### Changed
- Turn off lights when opening pipeline from notification.
- Turn off yellow light when showing success or failure.

## 1.15.0

### Added
- Add support for USB lights on Linux (thanks to Florian Micheler).

### Fixed
- Fix some exceptions.

## 1.14.0

### Added
- Add context menu entry for branches to open new merge request in browser.

### Fixed
- Fix three NullPointerExceptions.

## 1.13.0

### Fixed
- Actually don't crash by trying to load traffic lights DLL (on linux).

## 1.12.0

### Fixed
- Don't crash when trying to load traffic lights DLL (on linux).

### Added
- Expire notifications after 15 seconds.
- Remove separate configuration of statuses for which to create notifications. Follow generic notifications config.

## 1.11.0

### Fixed
- Don't spam notifications.

## 1.10.0

### Added
- Add button to turn off all lights.

### Fixed
- Keep red or green light on when showing yellow light.

## 1.9.0

### Added
- Support for USB lights (https://www.cleware-shop.de/).
- Add context menu to pipeline table.

## 1.8.0

### Changed
- Sort by branch name, then by time by default.

## 1.7.0

### Changed
- Use IntelliJ's HTTP library to connect to GitLab.

## 1.6.0

### Added
- Allow filtering by branch names.
- Add support for GitLab private access token.

### Changed
- Move refresh button to toolbar.
- Validate URL of gitlab in config.
- Add tooltip where to find the project ID.
- Show notifications for errors instead of writing to error log.
- Show max. 3 notifications for new pipeline statuses.

### Fixed
- Properly initialize repository on startup.

## 1.5.0

### Added
- Support for custom gitlab instances.

### Fixed
- Actually filter notifications.
