#!/usr/bin/env python3
"""AI hub source contracts: no simulated replies or silent editing permissions.

This is a static smoke test; only device tests and real API credentials can
prove provider access and task execution. Never use real keys in CI.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
java = (ROOT / "android/src/com/rbxnative/androidport/StudioAiHub.java").read_text()
manifest = (ROOT / "android/StudioManifest.xml").read_text()
native_patch = (ROOT / "patches/rbx-native--android-port.patch").read_text()
gpui_patch = (ROOT / "patches/gpui-mobile--android-port.patch").read_text()

assert 'android:name="com.rbxnative.androidport.StudioAiHub" android:exported="false"' in manifest
for provider, endpoint in [
    ("OpenAI", "https://api.openai.com/v1/models"),
    ("Gemini", "https://generativelanguage.googleapis.com/v1beta/models"),
    ("Claude", "https://api.anthropic.com/v1/models"),
    ("OpenRouter", "https://openrouter.ai/api/v1/models"),
]:
    assert provider in java and endpoint in java
for path in [
    "https://api.openai.com/v1/responses",
    "https://api.anthropic.com/v1/messages",
    "https://openrouter.ai/api/v1/chat/completions",
    ":generateContent",
]:
    assert path in java, path
for safety in [
    'StudioServices.credential(this, "write"',
    'StudioServices.credential(this, "read"',
    'StudioServices.credential(this, "delete"',
    "conn.setInstanceFollowRedirects(false)",
    'new Socket(InetAddress.getByName("127.0.0.1"), port)',
    'put("name", "search_game_tree")',
    'includeProject.setChecked(false)',
    'String message = include',
    'MAX_RESPONSE_BYTES',
]:
    assert safety in java, safety
assert '"execute_luau"' not in java, "Chat must not execute arbitrary Luau"
assert '"insert_asset"' not in java, "Chat must not import Toolbox models without review"
assert '"save_place"' not in java, "Chat must not persist projects without approval"
assert '"publish_animation"' not in java, "Chat must not publish assets without approval"
assert 'id("mobile-ai-providers"' not in native_patch  # action uses compact_action
assert '"mobile-ai-providers"' in native_patch
assert 'open_ai_hub(&name, &pairing)' in native_patch
assert 'pub fn open_ai_hub(project: &str, pairing: &str)' in gpui_patch
print("Android AI provider hub contracts passed (static; no live API keys used).")
