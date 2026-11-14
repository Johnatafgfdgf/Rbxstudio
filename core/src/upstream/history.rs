// Original: rbx-native/crates/rbx_studio/src/history.rs
//! Undo/redo: a bounded stack of whole-DOM snapshots, taken right before each
//! mutation (see `shell::history`, which calls [`History::push`] at every
//! call site `shell::command`, `shell::edit` and `shell::keys` already use to
//! take `Shell::dom` out, mutate it, and put it back), each paired with the
//! `Change` log that mutation went on to produce (see [`History::record_changes`]).
//!
//! Keeping a full [`WeakDom`] clone per entry rather than a diff is the same
//! trade `shell::command` already makes for a script run: places are small
//! enough in practice that a clone is cheap, and a diff format would have to
//! track every mutation path (script, property edit, insert, delete)
//! separately instead of once, here. The `Change` log carried alongside each
//! snapshot is that same log, not a second one: every mutation path already
//! writes to `WeakDom`'s own change log (`shell::command`'s `reflect_changes`
//! hands it to the viewport), so recording it here costs nothing beyond
//! draining it at the right two moments.

use rbx_dom::{Change, WeakDom};

/// How many undo steps are kept before the oldest is dropped, bounds memory
/// rather than growing the stack for the length of a whole editing session.
pub(crate) const DEFAULT_CAP: usize = 50;

/// One snapshot on either stack: the DOM as it stood at that point, plus the
/// `Change` log the mutation right after it went on to produce. Handed by
/// `shell::history::install` to `shell::command`'s `reflect_changes`: the
/// same path an ordinary edit's viewport reflection takes, so the viewport
/// patches exactly the instances the step touched, without re-diffing two
/// `WeakDom` trees to find them.
///
/// `changes` starts empty and is filled in once, after the fact, by
/// [`History::record_changes`], `push` runs *before* the mutation it
/// snapshots, so the log it produces isn't known yet. An entry whose
/// mutation never actually changed anything (a Properties-panel commit that
/// failed validation, say) simply keeps the empty log `push` left it with,
/// and reflecting an empty log is a no-op.
struct Entry {
    dom: WeakDom,
    changes: Vec<Change>,
}

/// A bounded stack of DOM snapshots either side of the current state: `undo`
/// holds what came before, `redo` holds what an undo just stepped back from.
pub(crate) struct History {
    undo: Vec<Entry>,
    redo: Vec<Entry>,
    cap: usize,
    /// Moves on every push, undo and redo, every way the DOM changes goes
    /// through one of the three, so a reader that only needs to know
    /// *whether* the tree moved since it last looked (`luau-lsp`'s copy of
    /// the place) can compare one number instead of two trees.
    revision: u64,
}

impl History {
    pub(crate) fn can_undo(&self) -> bool {
        !self.undo.is_empty()
    }

    pub(crate) fn can_redo(&self) -> bool {
        !self.redo.is_empty()
    }

    pub(crate) fn new(cap: usize) -> Self {
        History {
            undo: Vec::new(),
            redo: Vec::new(),
            cap,
            revision: 0,
        }
    }

    /// Snapshots `dom` onto the undo stack right before a mutation is
    /// applied to it, and clears the redo stack: a new action invalidates
    /// whatever could have been redone. Drops the oldest snapshot once the
    /// stack exceeds `cap`, so the stack never grows past it.
    pub(crate) fn push(&mut self, dom: WeakDom) {
        if self.undo.len() >= self.cap {
            self.undo.remove(0);
        }
        self.undo.push(Entry {
            dom,
            changes: Vec::new(),
        });
        self.redo.clear();
        self.revision += 1;
    }

    pub(crate) fn revision(&self) -> u64 {
        self.revision
    }

    /// Attaches `changes` to the entry `push` most recently added, see
    /// [`Entry`]'s doc comment. A no-op if nothing has been pushed yet
    /// (should not happen given `Shell::push_history`'s own call sites, but
    /// costs nothing to guard).
    pub(crate) fn record_changes(&mut self, changes: Vec<Change>) {
        if let Some(entry) = self.undo.last_mut() {
            entry.changes = changes;
        }
    }

    /// Pops the last snapshot, pushes `current` onto the redo stack, paired
    /// with the same `Change` log, since redoing this step reapplies exactly
    /// the mutation undoing it just reverted, and returns the popped DOM
    /// plus that log, for the caller to classify and install. `None` with
    /// nothing to undo, a no-op that leaves both stacks untouched.
    pub(crate) fn undo(&mut self, current: WeakDom) -> Option<(WeakDom, Vec<Change>)> {
        let previous = self.undo.pop()?;
        self.revision += 1;
        self.redo.push(Entry {
            dom: current,
            changes: previous.changes.clone(),
        });
        Some((previous.dom, previous.changes))
    }

    /// Symmetric to [`History::undo`]: pops the last undone snapshot, pushes
    /// `current` back onto the undo stack (with the same log, for the same
    /// reason `undo` carries it onto `redo`), and returns the popped DOM
    /// plus that log.
    pub(crate) fn redo(&mut self, current: WeakDom) -> Option<(WeakDom, Vec<Change>)> {
        let next = self.redo.pop()?;
        self.revision += 1;
        self.undo.push(Entry {
            dom: current,
            changes: next.changes.clone(),
        });
        Some((next.dom, next.changes))
    }
}

