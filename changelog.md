## [Unreleased]

## [2.6.0]

* Support for [merge request pipelines](https://docs.gitlab.com/ee/ci/pipelines/merge_request_pipelines.html)
* Update build plugins

## [2.5.0]

* Ensured compatibility with IntelliJ 2021.3. Due to a change in the notification API you will need to reconfigure the display type of the different status notifications (e.g. disable popups for running pipelines).

## [2.4.1]

* Hopefully prevent AlreadyDisposedException when projects are being closed and the plugin tries to execute an update in the background.

## [2.4.0]

* Check if CI is actually enabled for a remote before asking if it should be monitored. This is done using a "best guess" of gitlab host, project path and access token.

## [2.3.0]

* Support for personal and project access tokens. Personal access tokens work for all projects you're part of, project access tokens only for specific projects. The former is the default. Existing tokens are still saved per remote (i.e.
  considered project access tokens) but the next time you enter an access token you can select it to be a personal access token and it will be used for all your future projects on this host. You're still able to use project tokes for
  specific projects, even on hosts for which you already stored a personal access token.
* Instead of a modal dialog now a sticky balloon popup is displayed. Only when you click the contained action will the dialog show up. This should make opening multiple projects with untracked remotes easier.
* Fixed a bug where the project path determined from an HTTPS remote got truncated.
* Whenever an untracked HTTPS remote is found the list of already existing mappings is searched to see if one exists which matches the remote. That should make parsing of remotes more robust as for every host you only (may) have to fix the
  parsed values.

## [2.2.1]

* There's a bug in IntelliJ that causes progress bars to not close properly when the underlying task is finished too quickly. I thought I had found a way to work around it but sometimes it still happens. The bug is resolved in IntelliJ
  2021.2 but Jetbrains has told me they won't backport it to 2020.3.

## [2.2.0]

* Prevent "already disposed" error when quitting IntelliJ.
* Extend debug logging.
* Make gitlab host detection more robust.
* Add validation and tooltips to dialog for unmapped remotes.
* Make gitlab connect timeout configurable (5 seconds by default).
* Prevent freezing of UI while trying to reach slow or unavailable gitlab host.
* Reduce logging level of expected errors to info.
* Fix settings dialog being shown as dirty without any changes made.

## [2.1.1]

* Ignoring remotes until next restart didn't always work.

## [2.1.0]

* Show latest open merge request for a branch or allow to create a new one.
* Option to switch display of pipelines between URL, ID and icon.
* Don't show untracked remotes popup when one is already open.
* The dialog for untracked remotes will not display the determined gitlab host and project path. When choosing to monitor the remote's project the data will be verified instantly.
* Choosing to be asked again will ask the next time the plugin is loaded or a refresh is triggered manually (instead of asking every 15 seconds).
* Added a small manual: https://gitlab.com/ppiag/intellij\_gitlab\_pipeline\_monitor/-/blob/master/manual.md.

## [2.0.9]

* Continuous bug fixes.

## [2.0.2]

* Only remotes may be tracked where the UI is reachable under the remote's host. This will be changed in a future release.

## [2.0.1]

* Unfortunately due to a bug in IntelliJ with the progress indicator I released 2.0.0 with a last-minute update which caused a serious issue with a modal window freezing. This should be fixed for now but some progress indicators may be left
  dangling. I'm working on it. Sorry for the botched release :-/

## [2.0.0]

* Drastically changed workflow. The plugin will recognize all remotes used in an IntelliJ project and ask which should be monitored. It will then automatically retrieve all necessary data from gitlab. This a) reduces how much you need to
  configure and b) allows to decouple IntelliJ projects from Gitlab projects.
* Use gitlab default target branch for merge requests if none is configured.
* Option to show pipelines for tags.
* Display times in local time zone.
* Don't remove path of configured gitlab URL.
* Add action to open last pipeline in browser (located in Git main menu group).
* Add option to define custom command for opening URLs.
* Changed minimum IntelliJ version to 2020.1.

## [1.28.0]

* Ensure compatibility with new Gitlab API (ignore unknown values).

## [1.27.0]

* Fix exception when copying commit hash to clipboard.
* (Hopefully) fix exception when sorting branches without update time.

## [1.26.0]

* Add option to disable notifications for connection errors to gitlab<./li>

## [1.25.0]

* Only show the newest 3 notifications, don't spam the older ones.
* Improve compatibility with older IntelliJ 2019.3 versions.

## [1.24.0]

* Fix NPE when loading git repository.

## [1.23.0]

* Add button to copy current git hash to clipboard.
* Fix watched branches not being shown in form.
* Load and show pipelines on startup.

## [1.22.0]

* Fix exception when running plugin on Mac OS X.

## [1.21.0]

* Remove dependency on Apache Commons to fix compatibility with PyCharm.

## [1.20.0]

* Checkout branch from context menu.

## [1.19.0]

* Set compatibility to require at least IntelliJ 2019.3.

## [1.18.0]

* Add compatibility with IntelliJ 2020.1.

## [1.17.0]

* Add option to disable notifications for watched branches.

## [1.16.0]

* Turn off lights when opening pipeline from notification.
* Turn off yellow light when showing success or failure.

## [1.15.0]

* Add support for USB lights on Linux (thanks to Florian Micheler).
* Fix some exceptions.

## [1.14.0]

* Add context menu entry for branches to open new merge request in browser.
* Fix three NullPointerExceptions.

## [1.13.0]

* Actually don't crash by trying to load traffic lights DLL (on linux).

## [1.12.0]

* Don't crash when trying to load traffic lights DLL (on linux).
* Expire notifications after 15 seconds.
* Remove separate configuration of statuses for which to create notifications. Follow generic notifications config.

## [1.11.0]

* Don't spam notifications.

## [1.10.0]

* Keep red or green light on when showing yellow light.
* Add button to turn off all lights.

## [1.9.0]

* Support for USB lights (https://www.cleware-shop.de/).
* Add context menu to pipeline table.

## [1.8.0]

* Sort by branch name, then by time by default.

## [1.7.0]

* Use IntelliJ's HTTP library to connect to GitLab.

## [1.6.0]

* Move refresh button to toolbar.
* Allow filtering by branch names.
* Validate URL of gitlab in config.
* Add tooltip where to find the project ID.
* Properly initialize repository on startup.
* Show notifications for errors instead of writing to error log.
* Show max. 3 notifications for new pipeline statuses.
* Add support for GitLab private access token.

## [1.5.0]

* Actually filter notifications.
* Support for custom gitlab instances.
