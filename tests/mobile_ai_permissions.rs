// Regression test for Android's MCP approval boundary.
// Uses only the standard library so it can run in GitHub Actions before
// Android NDK compilation; the module is compiled from the patched source.
#[path = "../upstream/rbx-native/crates/rbx_studio/src/shell/ai_permissions.rs"]
mod ai_permissions;

#[test]
fn mobile_agent_access_is_explicit_and_revocable() {
    use ai_permissions::{risk, Risk};
    assert_eq!(risk("script_read"), Risk::Read);
    assert_eq!(risk("multi_edit"), Risk::Edit);
    assert_eq!(risk("execute_luau"), Risk::Confirm);
    assert!(!risk("script_read").needs_approval(false));
    assert!(risk("multi_edit").needs_approval(false));
    assert!(!risk("multi_edit").needs_approval(true));
    assert!(risk("insert_asset").needs_approval(true));
    assert!(risk("save_place").needs_approval(true));
    assert!(risk("invented_tool").needs_approval(true));
}
