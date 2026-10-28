# dmgbuild settings for Encore's disk image: Encore and an Applications link side by side over
# the branded background, in a window with no toolbar or sidebar. `scripts/make-dmg.sh` passes
# the paths in through `defines`.

app = defines["app"]  # noqa: F821
background = defines["background"]  # noqa: F821

format = "UDZO"
filesystem = "HFS+"
files = [app]
symlinks = {"Applications": "/Applications"}
icon = defines["icon"]  # noqa: F821
badge_icon = None

window_rect = ((200, 120), (660, 400))
default_view = "icon-view"
show_status_bar = False
show_tab_view = False
show_toolbar = False
show_pathbar = False
show_sidebar = False
include_icon_view_settings = True

arrange_by = None
icon_size = 112
text_size = 13
icon_locations = {
    "Encore.app": (180, 190),
    "Applications": (480, 190),
}
