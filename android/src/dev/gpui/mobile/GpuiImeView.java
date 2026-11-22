package dev.gpui.mobile;

import android.content.Context;
import android.os.Build;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.SurroundingText;

/** The Android input connection for the GPUI editor drawn on the native surface. */
final class GpuiImeView extends View {
    private static final int COMMIT = 0, COMPOSE = 1, FINISH = 2, DELETE = 3,
            DELETE_CODE_POINTS = 4, SELECT = 5, MARK = 6;
    private final GpuiActivity activity;
    private final InputMethodManager keyboard;
    private long session;
    private View previousFocus;
    private boolean multiline;

    GpuiImeView(GpuiActivity activity) {
        super(activity);
        this.activity = activity;
        keyboard = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        setFocusableInTouchMode(true);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setVisibility(GONE);
    }

    boolean isEditing() {
        return session != 0;
    }

    void setSession(long id, boolean show, boolean multi) {
        if (!show) {
            if (session != id) return;
            session = 0;
            keyboard.hideSoftInputFromWindow(getWindowToken(), 0);
            setVisibility(GONE);
            if (previousFocus != null) previousFocus.requestFocus();
            previousFocus = null;
            com.rbxnative.androidport.StudioWindow.configure(activity);
            return;
        }
        if (!isEditing()) previousFocus = activity.getCurrentFocus();
        boolean restart = session != id || multiline != multi;
        session = id;
        multiline = multi;
        // FLAG_FULLSCREEN disables adjustResize. Let the native surface resize
        // while the keyboard owns focus so the caret stays in the visible area.
        com.rbxnative.androidport.StudioWindow.configureWhileTyping(activity);
        setVisibility(VISIBLE);
        requestFocus();
        if (restart) keyboard.restartInput(this);
        post(() -> {
            if (session == id && isFocused()) keyboard.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT);
        });
    }

    void updateSelection(long id, int start, int end, int markedStart, int markedEnd) {
        if (session == id && isFocused()) {
            keyboard.updateSelection(this, start, end, markedStart, markedEnd);
        }
    }

    @Override public boolean onCheckIsTextEditor() {
        return isEditing();
    }

    @Override public InputConnection onCreateInputConnection(EditorInfo info) {
        if (!isEditing()) return null;
        long id = session;
        info.inputType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
        if (multiline) info.inputType |= InputType.TYPE_TEXT_FLAG_MULTI_LINE;
        info.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI | EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
                | (multiline ? EditorInfo.IME_ACTION_NONE : EditorInfo.IME_ACTION_DONE);
        Snapshot initial = new Snapshot(id, 256, 256);
        info.initialSelStart = initial.selectionStart;
        info.initialSelEnd = initial.selectionEnd;
        if (Build.VERSION.SDK_INT >= 30 && initial.text != null) {
            info.setInitialSurroundingSubText(initial.text, initial.offset);
        }
        return new Connection(id);
    }

    private static final class Snapshot {
        final String text;
        final int offset, selectionStart, selectionEnd;

        Snapshot(long session, int before, int after) {
            int[] selection = {-1, -1, -1};
            text = readIme(session, before, after, selection);
            offset = selection[0];
            selectionStart = selection[1];
            selectionEnd = selection[2];
        }
    }

    private final class Connection extends BaseInputConnection {
        private final long id;

        Connection(long id) {
            super(GpuiImeView.this, true);
            this.id = id;
        }

        private boolean edit(int operation, int first, int second, String text) {
            return session == id && editIme(id, operation, first, second, text);
        }

        @Override public boolean commitText(CharSequence text, int cursor) {
            return edit(COMMIT, cursor, 0, text.toString());
        }

        @Override public boolean setComposingText(CharSequence text, int cursor) {
            return edit(COMPOSE, cursor, 0, text.toString());
        }

        @Override public boolean finishComposingText() {
            return edit(FINISH, 0, 0, null);
        }

        @Override public boolean setComposingRegion(int start, int end) {
            return edit(MARK, start, end, null);
        }

        @Override public boolean deleteSurroundingText(int before, int after) {
            return edit(DELETE, before, after, null);
        }

        @Override public boolean deleteSurroundingTextInCodePoints(int before, int after) {
            return edit(DELETE_CODE_POINTS, before, after, null);
        }

        @Override public boolean setSelection(int start, int end) {
            return edit(SELECT, start, end, null);
        }

        @Override public boolean sendKeyEvent(KeyEvent event) {
            return session == id && keyIme(id, event.getKeyCode(), event.getAction(),
                    event.getMetaState(), event.getUnicodeChar());
        }

        @Override public boolean performContextMenuAction(int action) {
            int code;
            switch(action) {
                case android.R.id.selectAll: code=KeyEvent.KEYCODE_A;break;
                case android.R.id.copy: code=KeyEvent.KEYCODE_C;break;
                case android.R.id.cut: code=KeyEvent.KEYCODE_X;break;
                case android.R.id.paste:
                case android.R.id.pasteAsPlainText: code=KeyEvent.KEYCODE_V;break;
                default:return super.performContextMenuAction(action);
            }
            return session==id && keyIme(id,code,KeyEvent.ACTION_DOWN,KeyEvent.META_CTRL_ON,0)
                    && keyIme(id,code,KeyEvent.ACTION_UP,KeyEvent.META_CTRL_ON,0);
        }

        @Override public boolean performEditorAction(int action) {
            return sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                    && sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER));
        }

        @Override public CharSequence getTextBeforeCursor(int length, int flags) {
            Snapshot snapshot = new Snapshot(id, length, 0);
            if (snapshot.text == null) return null;
            return snapshot.text.substring(0, Math.min(snapshot.selectionStart,snapshot.selectionEnd) - snapshot.offset);
        }

        @Override public CharSequence getTextAfterCursor(int length, int flags) {
            Snapshot snapshot = new Snapshot(id, 0, length);
            if (snapshot.text == null) return null;
            return snapshot.text.substring(Math.max(snapshot.selectionStart,snapshot.selectionEnd) - snapshot.offset);
        }

        @Override public CharSequence getSelectedText(int flags) {
            return new Snapshot(id, 0, 0).text;
        }

        @Override public SurroundingText getSurroundingText(int before, int after, int flags) {
            if (Build.VERSION.SDK_INT < 31) return null;
            Snapshot snapshot = new Snapshot(id, before, after);
            if (snapshot.text == null) return null;
            return new SurroundingText(snapshot.text, snapshot.selectionStart - snapshot.offset,
                    snapshot.selectionEnd - snapshot.offset, snapshot.offset);
        }
    }

    private static native boolean editIme(long id, int operation, int first, int second, String text);
    private static native boolean keyIme(long id, int keyCode, int action, int metaState, int unicodeChar);
    private static native String readIme(long id, int before, int after, int[] selection);
}
