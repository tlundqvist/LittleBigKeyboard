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

import android.content.Context;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.inputmethodservice.Keyboard;

public class LatinKeyboard extends Keyboard {

	public static final int KEYCODE_LEFT    = -70;
	public static final int KEYCODE_RIGHT   = -71;
	public static final int KEYCODE_END     = -72;
	public static final int KEYCODE_HOME    = -73;
	public static final int KEYCODE_NOTUSED = -99;
    public static final int KEYCODE_OPTIONS = -100;
    public static final int KEYCODE_DEAD_ACUTE      = -120;
    public static final int KEYCODE_DEAD_GRAVE      = -121;
    public static final int KEYCODE_DEAD_DIARESIS   = -122;
    public static final int KEYCODE_DEAD_CIRCUMFLEX = -123;
    public static final int KEYCODE_DEAD_TILDE      = -124;
    
    private LatinKey mNumKey;
    
    public LatinKeyboard(Context context, int xmlLayoutResId) {
        super(context, xmlLayoutResId);
    }

    public LatinKeyboard(Context context, int layoutTemplateResId, 
            CharSequence characters, int columns, int horizontalPadding) {
        super(context, layoutTemplateResId, characters, columns, horizontalPadding);
    }

    @Override
    protected Key createKeyFromXml(Resources res, Row parent, int x, int y, 
            XmlResourceParser parser) {
        LatinKey key = new LatinKey(res, parent, x, y, parser);
        if (key.codes[0] == Keyboard.KEYCODE_MODE_CHANGE) {
        	mNumKey = key;
        }
        return key;
    }
    
    public void setNumMode(boolean nummode) {
    	if (mNumKey != null) {
    		mNumKey.setOn(nummode);
    	}
    }
    
    public class LatinKey extends Keyboard.Key {
        
        public LatinKey(Resources res, Keyboard.Row parent, int x, int y, XmlResourceParser parser) {
            super(res, parent, x, y, parser);
        }

        public void setPressed(boolean p) {
        	pressed = p;
        }
        
		public void setOn(boolean o) {
			if (sticky) {
				on = o;
			}
		}
    }

}
