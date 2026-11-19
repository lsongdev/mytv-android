# OhMyTV identity

Flat navy `#14263D`, mint `#69E1CF`, ivory `#F8F6F0`, coral `#FF9A76`.
A television/play mark is shared by the app, square icon, circular mask and banner.
The banner includes the full OhMyTV wordmark on a solid background.

- `icon.svg`: portable vector master with solid background.
- `foreground.svg`: adaptive foreground, using the 108×108 coordinate system.
- `banner.svg`: portable 16:9 master with the wordmark converted to paths.
- PNG exports: square, round and rounded 512×512 icons; 1280×720 banner.
- `preview.png`: mask and banner review sheet.

Android resources:

- Android 7: density-specific 80 dp square icons and 160×90 dp banners.
- Android 8+: adaptive icon and adaptive banner foreground/background layers.
- Android 13+ on launchers supporting themed icons: monochrome foreground.
- Google TV circular and square shapes use the system adaptive-icon mask; no
  separate deprecated `roundIcon` declaration is needed.
- xhdpi legacy files are 160×160 and 320×180 pixels respectively; all five
  standard density buckets are supplied.
- `ohmytv_mark` and `ohmytv_logo` are plain VectorDrawables for Compose image use,
  which avoids feeding an AdaptiveIconDrawable into painterResource.

Sources follow [Android TV icon guidance](https://developer.android.com/design/ui/tv/guides/system/tv-app-icon-guidelines)
and [adaptive icon guidance](https://developer.android.com/develop/ui/compose/system/icon_design_adaptive).
The mark is kept within the conservative central circular safe region.

To regenerate on macOS:

```sh
uv run tools/generate_brand_assets.py
```

The generator also accepts `--font /path/to/font.ttf --font-index 0`.
The current wordmark was outlined from Avenir Next Bold; font files are not
included and no font runtime dependency is added to the app. Committed SVGs
and Android vectors contain the finished paths.

The display name is OhMyTV. The package ID and the old DuckTV preferences
namespace remain stable so updates preserve sources, settings and canonical IDs.
