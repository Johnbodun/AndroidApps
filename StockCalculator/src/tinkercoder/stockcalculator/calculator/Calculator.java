/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package tinkercoder.stockcalculator.calculator;

import java.lang.reflect.Method;

import tinkercoder.stockcalculator.preferenceactivity.PreferenceSetting;
import tinkercoder.stockcalculator.stockcalculator.R;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowManager;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;

// import android.widget.PopupMenu;
// import android.widget.PopupMenu.OnMenuItemClickListener;

//import com.android.calculator2.R;

// import android.widget.PopupMenu;
// import android.widget.PopupMenu.OnMenuItemClickListener;

//import com.tombarrasso.android.calculator2.R;

public class Calculator extends
		tinkercoder.stockcalculator.navdrawer.DrawerActivity implements
		PanelSwitcher.Listener, Logic.Listener, OnClickListener {

	EventListener mListener = new EventListener();
	private CalculatorDisplay mDisplay;
	private Persist mPersist;
	private History mHistory;
	private Logic mLogic;
	private ViewPager mPager;
	private View mClearButton;
	private View mBackspaceButton;
	private View mOverflowMenuButton;
	private boolean isSingleUI = false;

	SharedPreferences sharedPref = null;
	PreferenceSetting prefSetting = new PreferenceSetting();
	private AdView adView;

	static final int BASIC_PANEL = 0;
	static final int ADVANCED_PANEL = 1;

	private static final String LOG_TAG = "Calculator";
	private static final boolean DEBUG = false;
	private static final boolean LOG_ENABLED = false;
	private static final String STATE_CURRENT_VIEW = "state-current-view";
	private static final int ID_IDENTIFIER = 914;

	private static final Class<ViewConfiguration> mViewClass = ViewConfiguration.class;
	private static Method mMethod;

	// Assume true...
	public static final boolean hasPermanentMenuKey(
			ViewConfiguration mViewConfig) {
		// Cache the Method for performance.
		if (mMethod == null) {
			try {
				// Check to see if an overscroll method exists.
				mMethod = mViewClass.getMethod("hasPermanentMenuKey",
						new Class[] {});
			} catch (NoSuchMethodException e) {
				return true;
			}
		}

		// Call the method if it exists.
		// It is bad practice to catch all exceptions
		// but Reflection has so many, all with the
		// same meaning that no method was called.
		try {
			return (Boolean) mMethod.invoke(mViewConfig, new Object[] {});
		} catch (Exception e) {
			return true;
		}
	}

	@Override
	public void onResume() {
		// TODO Auto-generated method stub
		super.onResume();
		if (adView != null) {
			adView.resume();
		}
	}

	@Override
	public void onDestroy() {
		// TODO Auto-generated method stub
		if (adView != null) {
			adView.destroy();
		}
		super.onDestroy();
	}

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(state);
		super.setInflaterOnView("calculator");
		super.getDrawerList().setItemChecked(0, true);

		// find and load ads
		AdView adView = (AdView) findViewById(R.id.adView);
		AdRequest adRequest = new AdRequest.Builder()
				.addTestDevice(AdRequest.DEVICE_ID_EMULATOR)
				.addTestDevice("98C719A04DF7111D2DDD25D764C88F8E").build();
		if (adView != null) {
			adView.loadAd(adRequest);
		}

		// Disable IME for this application
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM,
				WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);

		mPager = (ViewPager) findViewById(R.id.panelswitch);
		if (mPager != null) {
			isSingleUI = false;
			mPager.setAdapter(new PageAdapter(mPager));
		} else {
			// Single page UI
			isSingleUI = true;
			final TypedArray buttons = getResources().obtainTypedArray(
					R.array.buttons);
			for (int i = 0; i < buttons.length(); i++) {
				setOnClickListener(null, buttons.getResourceId(i, 0));
			}
			buttons.recycle();
		}

		if (mClearButton == null) {
			mClearButton = findViewById(R.id.clear);
			mClearButton.setOnClickListener(mListener);
			mClearButton.setOnLongClickListener(mListener);
		}
		if (mBackspaceButton == null) {
			mBackspaceButton = findViewById(R.id.del);
			mBackspaceButton.setOnClickListener(mListener);
			mBackspaceButton.setOnLongClickListener(mListener);
		}

		mPersist = new Persist(this);
		mPersist.load();

		mHistory = mPersist.history;

		mDisplay = (CalculatorDisplay) findViewById(R.id.display);

		mLogic = new Logic(this, mHistory, mDisplay);
		mLogic.setListener(this);

		mLogic.setDeleteMode(mPersist.getDeleteMode());
		mLogic.setLineLength(mDisplay.getMaxDigits());

		HistoryAdapter historyAdapter = new HistoryAdapter(this, mHistory,
				mLogic);
		mHistory.setObserver(historyAdapter);

		if (mPager != null) {
			mPager.setCurrentItem(state == null ? 0 : state.getInt(
					STATE_CURRENT_VIEW, 0));
		}

		mListener.setHandler(mLogic, mPager);
		mDisplay.setOnKeyListener(mListener);

		if (!hasPermanentMenuKey(ViewConfiguration.get(this))) {
			createFakeMenu();
		}

		mLogic.resumeWithHistory();
		updateDeleteMode();

		// launcher notification
		sharedPref = prefSetting.getSharedPreferences(this);

		if (sharedPref.getBoolean("disableNotification_calculator", false)) {
			// dont send notification
		} else {
			notificationLauncher();
		}
	}

	private void updateDeleteMode() {
		if (mLogic.getDeleteMode() == Logic.DELETE_MODE_BACKSPACE) {
			mClearButton.setVisibility(View.GONE);
			mBackspaceButton.setVisibility(View.VISIBLE);
		} else {
			mClearButton.setVisibility(View.VISIBLE);
			mBackspaceButton.setVisibility(View.GONE);
		}
	}

	void setOnClickListener(View root, int id) {
		final View target = root != null ? root.findViewById(id)
				: findViewById(id);
		target.setOnClickListener(mListener);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		getMenuInflater().inflate(R.menu.menu, menu);
		return true;
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		super.onPrepareOptionsMenu(menu);
		if (isSingleUI) {
			menu.findItem(R.id.basic).setVisible(getBasicVisibility());
			menu.findItem(R.id.advanced).setVisible(getAdvancedVisibility());
		} else {// multi UI
			menu.findItem(R.id.basic).setVisible(!getBasicVisibility());
			menu.findItem(R.id.advanced).setVisible(!getAdvancedVisibility());
		}
		return true;
	}

	private void createFakeMenu() {
		mOverflowMenuButton = findViewById(R.id.overflow_menu);
		if (mOverflowMenuButton != null) {
			mOverflowMenuButton.setVisibility(View.VISIBLE);
			mOverflowMenuButton.setOnClickListener(this);
		}
	}

	@Override
	public void onClick(View v) {
		switch (v.getId()) {
		case R.id.overflow_menu:
			/*
			 * PopupMenu menu = constructPopupMenu(); if (menu != null) {
			 * menu.show(); }
			 */
			break;
		}
	}

	/*
	 * private PopupMenu constructPopupMenu() { final PopupMenu popupMenu = new
	 * PopupMenu(this, mOverflowMenuButton); final Menu menu =
	 * popupMenu.getMenu(); popupMenu.inflate(R.menu.menu);
	 * popupMenu.setOnMenuItemClickListener(this); onPrepareOptionsMenu(menu);
	 * return popupMenu; }
	 */

	public boolean onMenuItemClick(MenuItem item) {
		return onOptionsItemSelected(item);
	}

	private boolean getBasicVisibility() {
		return mPager != null && mPager.getCurrentItem() == BASIC_PANEL;
	}

	private boolean getAdvancedVisibility() {
		return mPager != null && mPager.getCurrentItem() == ADVANCED_PANEL;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.clear_history:
			mHistory.clear();
			mLogic.onClear();
			break;

		case R.id.basic:
			if (!getBasicVisibility()) {
				mPager.setCurrentItem(BASIC_PANEL);
			}
			break;

		case R.id.advanced:
			if (!getAdvancedVisibility()) {
				mPager.setCurrentItem(ADVANCED_PANEL);
			}
			break;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	protected void onSaveInstanceState(Bundle state) {
		super.onSaveInstanceState(state);
		if (mPager != null) {
			state.putInt(STATE_CURRENT_VIEW, mPager.getCurrentItem());
		}
	}

	@Override
	public void onPause() {
		if (adView != null) {
			adView.pause();
		}
		super.onPause();
		mLogic.updateHistory();
		mPersist.setDeleteMode(mLogic.getDeleteMode());
		mPersist.save();
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent keyEvent) {
		if (keyCode == KeyEvent.KEYCODE_BACK && getAdvancedVisibility()) {
			mPager.setCurrentItem(BASIC_PANEL);
			return true;
		} else {
			return super.onKeyDown(keyCode, keyEvent);
		}
	}

	static void log(String message) {
		if (LOG_ENABLED) {
			Log.v(LOG_TAG, message);
		}
	}

	@Override
	public void onChange() {
		getWindow().getDecorView().invalidate();
	}

	@Override
	public void onDeleteModeChange() {
		updateDeleteMode();
	}

	protected void notificationLauncher() {
		PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
				new Intent(this, PreferenceSetting.class), 0);

		NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(
				this).setContentTitle(getString(R.string.notification_title))
				.setContentText(getString(R.string.notification_text))
				.setSmallIcon(R.drawable.ic_launcher).setAutoCancel(true)
				.setContentIntent(contentIntent);

		NotificationManager notifManager = (NotificationManager) this
				.getSystemService(Context.NOTIFICATION_SERVICE);
		notifManager.notify(ID_IDENTIFIER, mBuilder.build());
	}

	class PageAdapter extends PagerAdapter {
		private View mSimplePage;
		private View mAdvancedPage;

		public PageAdapter(ViewPager parent) {
			final LayoutInflater inflater = LayoutInflater.from(parent
					.getContext());
			final View simplePage = inflater.inflate(R.layout.simple_pad,
					parent, false);
			final View advancedPage = inflater.inflate(R.layout.advanced_pad,
					parent, false);
			mSimplePage = simplePage;
			mAdvancedPage = advancedPage;

			final Resources res = getResources();
			final TypedArray simpleButtons = res
					.obtainTypedArray(R.array.simple_buttons);
			for (int i = 0; i < simpleButtons.length(); i++) {
				setOnClickListener(simplePage,
						simpleButtons.getResourceId(i, 0));
			}
			simpleButtons.recycle();

			final TypedArray advancedButtons = res
					.obtainTypedArray(R.array.advanced_buttons);
			for (int i = 0; i < advancedButtons.length(); i++) {
				setOnClickListener(advancedPage,
						advancedButtons.getResourceId(i, 0));
			}
			advancedButtons.recycle();

			final View clearButton = simplePage.findViewById(R.id.clear);
			if (clearButton != null) {
				mClearButton = clearButton;
			}

			final View backspaceButton = simplePage.findViewById(R.id.del);
			if (backspaceButton != null) {
				mBackspaceButton = backspaceButton;
			}
		}

		@Override
		public int getCount() {
			return 2;
		}

		@Override
		public void startUpdate(View container) {
		}

		@Override
		public Object instantiateItem(View container, int position) {
			final View page = position == 0 ? mSimplePage : mAdvancedPage;
			((ViewGroup) container).addView(page);
			return page;
		}

		@Override
		public void destroyItem(View container, int position, Object object) {
			((ViewGroup) container).removeView((View) object);
		}

		@Override
		public void finishUpdate(View container) {
		}

		@Override
		public boolean isViewFromObject(View view, Object object) {
			return view == object;
		}

		@Override
		public Parcelable saveState() {
			return null;
		}

		@Override
		public void restoreState(Parcelable state, ClassLoader loader) {
		}
	}
}
