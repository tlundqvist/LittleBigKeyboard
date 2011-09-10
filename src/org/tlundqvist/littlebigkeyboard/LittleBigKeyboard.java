/*
 * Copyright (C) 2011 Thomas Lundqvist
 * Copyright (C) 2008-2009 Google Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package org.tlundqvist.littlebigkeyboard;

import android.inputmethodservice.InputMethodService;
import android.inputmethodservice.Keyboard;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.CompletionInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

import java.util.ArrayList;
import java.util.List;

import org.tlundqvist.littlebigkeyboard.R;

/**
 * Input method for a soft keyboard.  Based on the android sample code: softkeyboard.
 */
public class LittleBigKeyboard extends InputMethodService
        implements ModKeyboardView.OnKeyboardActionListener {
	
    static final boolean DEBUG = false;
    
    private ModKeyboardView mInputView;
    private CandidateView mCandidateView;
    private CompletionInfo[] mCompletions;
    
    // Composing is only used for dead key accent combinations (maximum length 1)
    private char mDeadKeyComposing;
    private boolean mCompletionOn;
    private boolean mCapsLock;
    private long mLastShiftTime;
    
    private LatinKeyboard mAlphaKeyboard;
    private LatinKeyboard mAlphaShiftedKeyboard;
    private LatinKeyboard mNumeralKeyboard;
    private LatinKeyboard mNumeralShiftedKeyboard;
    
    private LatinKeyboard mCurKeyboard;
    
    /**
     * Main initialization of the input method component.  Be sure to call
     * to super class.
     */
    @Override public void onCreate() {
        super.onCreate();
        // Use the following line to debug IME service.
        if (DEBUG)
        	android.os.Debug.waitForDebugger();
    }
    
    /**
     * This is the point where you can do all of your UI initialization.  It
     * is called after creation and any configuration change.
     */
    private int mLastDisplayWidth;
    
    @Override public void onInitializeInterface() {
        int displayWidth = getMaxWidth();
        if (mAlphaKeyboard != null) {
            // Configuration changes can happen after the keyboard gets recreated,
            // so we avoid re-building the keyboards if the available
            // space hasn't changed.
            if (displayWidth == mLastDisplayWidth) return;
        }
        mLastDisplayWidth = displayWidth;
        
        mAlphaKeyboard          = new LatinKeyboard(this, R.xml.alpha);
        mAlphaShiftedKeyboard   = new LatinKeyboard(this, R.xml.alpha_shifted);
        mNumeralKeyboard        = new LatinKeyboard(this, R.xml.numeral);
        mNumeralShiftedKeyboard = new LatinKeyboard(this, R.xml.numeral_shifted);
    }
    
    /**
     * Called by the framework when your view for creating input needs to
     * be generated.  This will be called the first time your input method
     * is displayed, and every time it needs to be re-created such as due to
     * a configuration change.
     */
    @Override public View onCreateInputView() {
        mInputView = (ModKeyboardView) getLayoutInflater().inflate(
                R.layout.input, null);
        mInputView.setOnKeyboardActionListener(this);
        return mInputView;
    }

    /**
     * Called by the framework when your view for showing candidates needs to
     * be generated, like {@link #onCreateInputView}.
     */
    @Override public View onCreateCandidatesView() {
        mCandidateView = new CandidateView(this);
        mCandidateView.setService(this);
        return mCandidateView;
    }

    /**
     * This is the main point where we do our initialization of the input method
     * to begin operating on an application.  At this point we have been
     * bound to the client, and are now receiving all of the detailed information
     * about the target of our edits.
     */
    @Override public void onStartInput(EditorInfo attribute, boolean restarting) {
        super.onStartInput(attribute, restarting);
        
        // Reset our state.  We want to do this even if restarting, because
        // the underlying state of the text editor could have changed in any way.
        mDeadKeyComposing = 0;
        
        mCompletionOn = false;
        mCompletions = null;
        
        // We are now going to initialize our state based on the type of
        // text being edited.
        switch (attribute.inputType&EditorInfo.TYPE_MASK_CLASS) {
            case EditorInfo.TYPE_CLASS_NUMBER:
            case EditorInfo.TYPE_CLASS_DATETIME:
                // Numbers and dates default to the symbols keyboard, with
                // no extra features.
                mCurKeyboard = mNumeralKeyboard;
                break;
                
            case EditorInfo.TYPE_CLASS_PHONE:
                // Phones will also default to the symbols keyboard, though
                // often you will want to have a dedicated phone keyboard.
                mCurKeyboard = mNumeralKeyboard;
                break;
                
            case EditorInfo.TYPE_CLASS_TEXT:
                // This is general text editing.  We will default to the
                // normal alphabetic keyboard, and assume that we should
                // be doing predictive text (showing candidates as the
                // user types).
                mCurKeyboard = mAlphaKeyboard;
                
                if ((attribute.inputType&EditorInfo.TYPE_TEXT_FLAG_AUTO_COMPLETE) != 0) {
                    // If this is an auto-complete text view, then our predictions
                    // will not be shown and instead we will allow the editor
                    // to supply their own.  We only show the editor's
                    // candidates when in fullscreen mode, otherwise relying
                    // on it displaying its own UI.
                    mCompletionOn = isFullscreenMode();
                }
                break;
                
            default:
                // For all unknown input types, default to the alphabetic
                // keyboard with no special features.
                mCurKeyboard = mAlphaKeyboard;
        }
    }

    /**
     * This is called when the user is done editing a field.  We can use
     * this to reset our state.
     */
    @Override public void onFinishInput() {
        super.onFinishInput();
        
        // Clear current composing text
        mDeadKeyComposing = 0;
        
        // We only hide the candidates window when finishing input on
        // a particular editor, to avoid popping the underlying application
        // up and down if the user is entering text into the bottom of
        // its window.
        setCandidatesViewShown(false);
        
        mCurKeyboard = mAlphaKeyboard;
    }
    
    @Override public void onStartInputView(EditorInfo attribute, boolean restarting) {
        super.onStartInputView(attribute, restarting);
        // Apply the selected keyboard to the input view.
        mInputView.setKeyboard(mCurKeyboard);
        updateShiftKeyState();
    }
    
    /**
     * Deal with the editor reporting movement of its cursor.
     */
    @Override public void onUpdateSelection(int oldSelStart, int oldSelEnd,
            int newSelStart, int newSelEnd,
            int candidatesStart, int candidatesEnd) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd,
                candidatesStart, candidatesEnd);
        
        // If the current selection in the text view changes, we should
        // clear whatever candidate text we have.
        if (mDeadKeyComposing != 0 && (newSelStart != candidatesEnd
                || newSelEnd != candidatesEnd)) {
            mDeadKeyComposing = 0;
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) {
                ic.finishComposingText();
            }
        }
    }

    /**
     * This tells us about completions that the editor has determined based
     * on the current text in it.  We want to use this in fullscreen mode
     * to show the completions ourself, since the editor can not be seen
     * in that situation.
     */
    @Override public void onDisplayCompletions(CompletionInfo[] completions) {
        if (mCompletionOn) {
            mCompletions = completions;
            if (completions == null) {
                setSuggestions(null, false, false);
                return;
            }
            
            List<String> stringList = new ArrayList<String>();
            for (int i=0; i<(completions != null ? completions.length : 0); i++) {
                CompletionInfo ci = completions[i];
                if (ci != null) stringList.add(ci.getText().toString());
            }
            setSuggestions(stringList, true, true);
        }
    }
    
    /**
     * Helper function to commit any text being composed in to the editor.
     */
    private void commitTyped(InputConnection inputConnection) {
        if (mDeadKeyComposing != 0) {
            inputConnection.commitText(Character.toString(mDeadKeyComposing), 1);
            mDeadKeyComposing = 0;
        }
    }

    /**
     * Helper to update the shift state of our keyboard based on the initial
     * editor state.
     */
    private void updateShiftKeyState() {
    	EditorInfo attr = getCurrentInputEditorInfo();
        if (attr != null 
        		&& mInputView != null) {
            int caps = 0;
        	if (mAlphaKeyboard == mInputView.getKeyboard() ||
        			mAlphaShiftedKeyboard == mInputView.getKeyboard()) {
	            EditorInfo ei = getCurrentInputEditorInfo();
	            if (ei != null && ei.inputType != EditorInfo.TYPE_NULL) {
	                caps = getCurrentInputConnection().getCursorCapsMode(attr.inputType);
	            }
        	}
            setShifted(mCapsLock || caps != 0);
        }
    }
    
    private void setShifted(boolean shifted) {
        Keyboard currentKeyboard = mInputView.getKeyboard();
    	if (shifted) {
    		if (currentKeyboard == mAlphaKeyboard)
    			mInputView.setKeyboard(mAlphaShiftedKeyboard);
    		else if (currentKeyboard == mNumeralKeyboard)
    			mInputView.setKeyboard(mNumeralShiftedKeyboard);
    	} else {
    		if (currentKeyboard == mAlphaShiftedKeyboard)
    			mInputView.setKeyboard(mAlphaKeyboard);
    		else if (currentKeyboard == mNumeralShiftedKeyboard)
    			mInputView.setKeyboard(mNumeralKeyboard);
    	}
        mInputView.setShifted(mCapsLock, shifted);
        boolean nummode = currentKeyboard == mNumeralKeyboard ||
    		currentKeyboard == mNumeralShiftedKeyboard;
        mInputView.setNumMode(nummode);
    }
    
    // Implementation of KeyboardViewListener
    
    public void onKey(int primaryCode) {
    	if (mInputView == null) return;
    	
    	if (primaryCode == Keyboard.KEYCODE_DELETE) {
            handleBackspace();
        } else if (primaryCode == Keyboard.KEYCODE_SHIFT) {
            handleShift();
        } else if (primaryCode == Keyboard.KEYCODE_CANCEL) {
            handleClose();
        } else if (primaryCode == LatinKeyboard.KEYCODE_OPTIONS) {
            // Show a menu or somethin'
        } else if (primaryCode == Keyboard.KEYCODE_MODE_CHANGE) {
        	handleModeChange();
        } else if (primaryCode == LatinKeyboard.KEYCODE_LEFT) {
        	sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_LEFT);
        } else if (primaryCode == LatinKeyboard.KEYCODE_RIGHT) {
        	sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_RIGHT);
        } else if (primaryCode == LatinKeyboard.KEYCODE_END) {
        	for (int i = 0 ; i < 5 ; i++) {
            	sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_RIGHT);
        	}
        } else if (primaryCode == LatinKeyboard.KEYCODE_HOME) {
    		getCurrentInputConnection().setSelection(0,0);
        } else if (primaryCode == LatinKeyboard.KEYCODE_DEAD_ACUTE) {
        	handleDeadKey('\u00b4');
        } else if (primaryCode == LatinKeyboard.KEYCODE_DEAD_GRAVE) {
        	handleDeadKey('`');
        } else if (primaryCode == LatinKeyboard.KEYCODE_DEAD_DIARESIS) {
        	handleDeadKey('\u00a8');
        } else if (primaryCode == LatinKeyboard.KEYCODE_DEAD_CIRCUMFLEX) {
        	handleDeadKey('^');
        } else if (primaryCode == LatinKeyboard.KEYCODE_DEAD_TILDE) {
        	handleDeadKey('~');
        } else {
        	// normal character or separator
            if (mDeadKeyComposing != 0) {
            	// Compose with previous accent
            	int r = (char) KeyEvent.getDeadChar(mDeadKeyComposing, primaryCode);
            	if (r != 0)
            		mDeadKeyComposing = (char) r;
                commitTyped(getCurrentInputConnection());
            } else {
            	sendKeyChar((char)primaryCode);
            }
        	updateShiftKeyState();
        }
    }

    public void onText(CharSequence text) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        ic.beginBatchEdit();
        ic.commitText(text, 0);
        ic.endBatchEdit();
        updateShiftKeyState();
    }

    public void setSuggestions(List<String> suggestions, boolean completions,
            boolean typedWordValid) {
        if (suggestions != null && suggestions.size() > 0) {
            setCandidatesViewShown(true);
        } else if (isExtractViewShown()) {
            setCandidatesViewShown(true);
        }
        if (mCandidateView != null) {
            mCandidateView.setSuggestions(suggestions, completions, typedWordValid);
        }
    }
    
    private void handleBackspace() {
        if (mDeadKeyComposing != 0) {
            mDeadKeyComposing = 0;
            getCurrentInputConnection().setComposingText("", 0);
        } else {
        	sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL);
        }
        updateShiftKeyState();
    }

    private void handleShift() {
        checkToggleCapsLock();
        boolean shifted = mCapsLock || !mInputView.isShifted();
        setShifted(shifted);
    }
    
    private void handleModeChange() {
	    LatinKeyboard current = mInputView.getKeyboard();
	    if (current == mNumeralKeyboard || current == mNumeralShiftedKeyboard) {
	        current = mAlphaKeyboard;
	    } else {
	        current = mNumeralKeyboard;
	    }
	    mInputView.setKeyboard(current);
	    mCapsLock = false;
	    setShifted(false);
	    updateShiftKeyState();
    }
    
    private void handleClose() {
        commitTyped(getCurrentInputConnection());
        requestHideSelf(0);
    }

    private void checkToggleCapsLock() {
        long now = System.currentTimeMillis();
        if (mLastShiftTime + 600 > now) {
            mCapsLock = true;
            mLastShiftTime = now;
        } else {
            mLastShiftTime = now;
            mCapsLock = false;
        }
    }
    
    private void handleDeadKey(char accent) {
        mDeadKeyComposing = accent;
        getCurrentInputConnection().setComposingText(Character.toString(mDeadKeyComposing), 1);
    	updateShiftKeyState();
    }
    
//    public void pickDefaultCandidate() {
//        pickSuggestionManually(0);
//    }
    
    public void pickSuggestionManually(int index) {
        if (mCompletionOn && mCompletions != null && index >= 0
                && index < mCompletions.length) {
            CompletionInfo ci = mCompletions[index];
            getCurrentInputConnection().commitCompletion(ci);
            if (mCandidateView != null) {
                mCandidateView.clear();
            }
            updateShiftKeyState();
        }
    }
    
    public void onPress(int primaryCode) {
    }
    
    public void onRelease(int primaryCode) {
    }
}
