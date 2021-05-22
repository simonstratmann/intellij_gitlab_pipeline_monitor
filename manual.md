# Overview

This plugin allows you to monitor and display pipelines run on gitlab. Both gitlab.com and self-hosted instances are supported.
![Screenshot main window](https://i.imgur.com/TtKN9p3.png)

By default all pipelines for tags and locally checked out branches are monitored. For each branch the last finished pipeline and a currently running pipeline (if one exists) will be shown.

When the plugin detects a git remote that is unknown it will ask you if you want to monitor pipelines for this (assuming it's associated with a gitlab project):
![Screenshot untracked remote window](https://i.imgur.com/uy6Wlgp.png)
