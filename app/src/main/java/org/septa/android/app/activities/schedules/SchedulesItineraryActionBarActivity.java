/*
 * SchedulesItineraryActionBarActivity.java
 * Last modified on 05-12-2014 09:45-0400 by brianhmayo
 *
 * Copyright (c) 2014 SEPTA.  All rights reserved.
 */

package org.septa.android.app.activities.schedules;

import android.annotation.TargetApi;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.septa.android.app.R;
import org.septa.android.app.activities.BaseAnalyticsActionBarActivity;
import org.septa.android.app.activities.FareInformationActionBarActivity;
import org.septa.android.app.activities.NextToArriveRealTimeWebViewActionBarActivity;
import org.septa.android.app.activities.NextToArriveStopSelectionActionBarActivity;
import org.septa.android.app.adapters.schedules.Schedules_Itinerary_MenuDialog_ListViewItem_ArrayAdapter;
import org.septa.android.app.adapters.schedules.SchedulesItinerary_ListViewItem_ArrayAdapter;
import org.septa.android.app.managers.SchedulesFavoritesAndRecentlyViewedStore;
import org.septa.android.app.models.ObjectFactory;
import org.septa.android.app.models.RouteTypes;
import org.septa.android.app.models.SchedulesDataModel;
import org.septa.android.app.models.SchedulesFavoriteModel;
import org.septa.android.app.models.SchedulesRecentlyViewedModel;
import org.septa.android.app.models.SchedulesRouteModel;
import org.septa.android.app.models.TripObject;
import org.septa.android.app.models.adapterhelpers.TextSubTextImageModel;

import java.util.ArrayList;

import se.emilsjolander.stickylistheaders.StickyListHeadersListView;

import static org.septa.android.app.models.RouteTypes.valueOf;

public class SchedulesItineraryActionBarActivity  extends BaseAnalyticsActionBarActivity implements
        AdapterView.OnItemClickListener, StickyListHeadersListView.OnHeaderClickListener,
        StickyListHeadersListView.OnStickyHeaderOffsetChangedListener,
        StickyListHeadersListView.OnStickyHeaderChangedListener, View.OnTouchListener {
    public static final String TAG = SchedulesItineraryActionBarActivity.class.getName();

    private SchedulesItinerary_ListViewItem_ArrayAdapter mAdapter;
    private boolean fadeHeader = true;

    private RouteTypes travelType;

    private StickyListHeadersListView stickyList;

    private ArrayList<SchedulesRouteModel> routesModel;

    private String iconImageNameSuffix;

    private final String[] tabLabels = new String[]{"NOW", "WEEKDAY", "SATURDAY", "SUNDAY"};
    private int selectedTab = 0;

    private boolean inProcessOfStartDestinationFlow;

    private SchedulesRouteModel schedulesRouteModel = new SchedulesRouteModel();

    private boolean menuRevealed = false;
    private ListView menuDialogListView;

    private CountDownTimer schedulesItineraryRefreshCountDownTimer;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.schedules_itinerary);

        String actionBarTitleText = getIntent().getStringExtra(getString(R.string.actionbar_titletext_key));

        iconImageNameSuffix = getIntent().getStringExtra(getString(R.string.actionbar_iconimage_imagenamesuffix_key));
        String resourceName = getString(R.string.actionbar_iconimage_imagename_base).concat(iconImageNameSuffix);

        int id = getResources().getIdentifier(resourceName, "drawable", getPackageName());

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle("| " + actionBarTitleText);
        getSupportActionBar().setIcon(id);

        String stringTravelType = getIntent().getStringExtra(getString(R.string.schedules_itinerary_travelType));
        if (stringTravelType != null) {
            travelType = valueOf(getIntent().getStringExtra(getString(R.string.schedules_itinerary_travelType)));
        } else {
            Log.d("f", "travelType is null...");
        }

        this.inProcessOfStartDestinationFlow = false;

        mAdapter = new SchedulesItinerary_ListViewItem_ArrayAdapter(this, travelType);

        String schedulesRouteModelJSONString = getIntent().getStringExtra(getString(R.string.schedules_itinerary_schedulesRouteModel));
        Gson gson = new Gson();
        schedulesRouteModel = gson.fromJson(schedulesRouteModelJSONString, new TypeToken<SchedulesRouteModel>() {
        }.getType());


        String iconPrefix = getResources().getString(R.string.schedules_itinerary_menu_icon_imageBase);
        String[] texts = getResources().getStringArray(R.array.schedules_itinerary_menu_listview_items_texts);
        String[] subTexts = getResources().getStringArray(R.array.schedules_itinerary_menu_listview_items_subtexts);
        String[] iconSuffix = getResources().getStringArray(R.array.schedules_itinerary_menu_listview_items_iconsuffixes);
        TextSubTextImageModel[] listMenuItems = new TextSubTextImageModel[texts.length];
        for (int i = 0; i < texts.length; i++) {
            TextSubTextImageModel textSubTextImageModel = new TextSubTextImageModel(texts[i], subTexts[i], iconPrefix, iconSuffix[i]);
            listMenuItems[i] = textSubTextImageModel;
        }

        ListView menuListView = (ListView) findViewById(R.id.schedules_itinerary_menudialog_listview);
        this.menuDialogListView = menuListView;
        menuListView.setAdapter(new Schedules_Itinerary_MenuDialog_ListViewItem_ArrayAdapter(this, listMenuItems));

        if (schedulesRouteModel != null && schedulesRouteModel.hasStartAndEndSelected()) {
            mAdapter.setRouteStartName(schedulesRouteModel.getRouteStartName());
            mAdapter.setRouteEndName(schedulesRouteModel.getRouteEndName());

            checkTripStartAndDestinationForNextToArriveDataRequest();
        }

        stickyList = (StickyListHeadersListView) findViewById(R.id.list);
        stickyList.setOnItemClickListener(this);
        stickyList.setOnHeaderClickListener(this);
        stickyList.setOnStickyHeaderChangedListener(this);
        stickyList.setOnStickyHeaderOffsetChangedListener(this);
        stickyList.setEmptyView(findViewById(R.id.empty));
        stickyList.setDrawingListUnderStickyHeader(true);
        stickyList.setAreHeadersSticky(true);
        stickyList.setAdapter(mAdapter);
        stickyList.setOnTouchListener(this);

        stickyList.setFastScrollEnabled(true);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);

        routesModel = new ArrayList<SchedulesRouteModel>();
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        View routeDirectionView = (View) findViewById(R.id.schedules_itinerary_routedirection_view);

        // get the color from the looking array given the ordinal position of the route type
        String color = this.getResources().getStringArray(R.array.schedules_routeselection_routesheader_solid_colors)[travelType.ordinal()];
        routeDirectionView.setBackgroundColor(Color.parseColor(color));

        Button tabNowButton = (Button) findViewById(R.id.schedules_itinerary_tab_now_button);
        Button tabWeekdayButton = (Button) findViewById(R.id.schedules_itinerary_tab_weekday_button);
        Button tabSatButton = (Button) findViewById(R.id.schedules_itinerary_tab_sat_button);
        Button tabSunButton = (Button) findViewById(R.id.schedules_itinerary_tab_sun_button);

        GradientDrawable nowTabButtonShapeDrawable = (GradientDrawable) tabNowButton.getBackground();
        GradientDrawable weekdayTabButtonShapeDrawable = (GradientDrawable) tabWeekdayButton.getBackground();
        GradientDrawable satTabButtonShapeDrawable = (GradientDrawable) tabSatButton.getBackground();
        GradientDrawable sunTabButtonShapeDrawable = (GradientDrawable) tabSunButton.getBackground();

        nowTabButtonShapeDrawable.setColor(Color.parseColor(color));
        tabNowButton.setTextColor(Color.WHITE);
        weekdayTabButtonShapeDrawable.setColor(Color.LTGRAY);
        tabWeekdayButton.setTextColor(Color.BLACK);
        satTabButtonShapeDrawable.setColor(Color.LTGRAY);
        tabSatButton.setTextColor(Color.BLACK);
        sunTabButtonShapeDrawable.setColor(Color.LTGRAY);
        tabSunButton.setTextColor(Color.BLACK);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {

        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.nexttoarrive_action_bar, menu);

        FrameLayout menuDialog = (FrameLayout) findViewById(R.id.schedules_itinerary_menudialog_mainlayout);
        menuDialog.setBackgroundResource(R.drawable.nexttoarrive_menudialog_bottomcorners);
        GradientDrawable drawable = (GradientDrawable) menuDialog.getBackground();
        drawable.setColor(0xFF353534);

//        String iconPrefix = getResources().getString(R.string.schedules_itinerary_menu_icon_imageBase);
//        String[] texts = getResources().getStringArray(R.array.schedules_itinerary_menu_listview_items_texts);
//        String[] subTexts = getResources().getStringArray(R.array.schedules_itinerary_menu_listview_items_subtexts);
//        String[] iconSuffix = getResources().getStringArray(R.array.schedules_itinerary_menu_listview_items_iconsuffixes);

//        TextSubTextImageModel[] listMenuItems = new TextSubTextImageModel[texts.length];
//        for (int i=0; i<texts.length; i++) {
//            TextSubTextImageModel textSubTextImageModel = new TextSubTextImageModel(texts[i], subTexts[i], iconPrefix, iconSuffix[i]);
//            listMenuItems[i] = textSubTextImageModel;
//        }
//
//        ListView menuListView = (ListView)findViewById(R.id.schedules_itinerary_menudialog_listview);
//        this.menuDialogListView = menuListView;
//        menuListView.setAdapter(new Schedules_Itinerary_MenuDialog_ListViewItem_ArrayAdapter(this, listMenuItems));

        menuDialogListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

                switch (position) {
                    case 0: {       // favorite
                        addOrRemoveRouteFromFavorites();
//                        mAdapter.reloadFavoriteAndRecentlyViewedLists();
                        mAdapter.notifyDataSetChanged();
                        hideListView();
                        break;
                    }
                    case 1: {       // fare
                        startActivity(new Intent(SchedulesItineraryActionBarActivity.this,
                                FareInformationActionBarActivity.class));
                        hideListView();
                        break;
                    }
                    case 2: {       // service advisory
                        checkTripStartAndDestinationForNextToArriveDataRequest();
                        hideListView();
                        break;
                    }
                    case 3: {       // real time
                        startActivity(new Intent(SchedulesItineraryActionBarActivity.this,
                                NextToArriveRealTimeWebViewActionBarActivity.class));
                        hideListView();
                        break;
                    }
                }
            }
        });

        return true;
    }

    private void addOrRemoveRouteFromFavorites() {
        SchedulesFavoritesAndRecentlyViewedStore store = ObjectFactory.getInstance().getSchedulesFavoritesAndRecentlyViewedStore(this);

        SchedulesFavoriteModel schedulesFavoriteModel = new SchedulesFavoriteModel();
        schedulesFavoriteModel.setRouteId(schedulesRouteModel.getRouteId());
        schedulesFavoriteModel.setRouteStartStopId(schedulesRouteModel.getRouteStartStopId());
        schedulesFavoriteModel.setRouteStartName(schedulesRouteModel.getRouteStartName());
        schedulesFavoriteModel.setRouteEndStopId(schedulesRouteModel.getRouteEndStopId());
        schedulesFavoriteModel.setRouteEndName(schedulesRouteModel.getRouteEndName());

        // check if the selected route is already a favorite, then we allow the option of removing this
        // route from the favorites list.
        if (store.isFavorite(this.travelType.name(), schedulesFavoriteModel)) {
            Log.d("tt", "detected this is a favorite, remove ");
            store.removeFavorite(this.travelType.name(), schedulesFavoriteModel);
            ((Schedules_Itinerary_MenuDialog_ListViewItem_ArrayAdapter) menuDialogListView.getAdapter()).disableRemoveSavedFavorite();
        } else {
            Log.d("tt", "adding as a favorite");
            store.addFavorite(this.travelType.name(), schedulesFavoriteModel);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.actionmenu_nexttoarrive_revealactions:

                if (menuRevealed) {

                    hideListView();
                } else {

                    revealListView();
                }

                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void revealListView() {
        menuRevealed = true;

        Log.d(TAG, "reveal menu");

        FrameLayout menuDialog = (FrameLayout) findViewById(R.id.schedules_itinerary_menudialog_mainlayout);
        ListView listView = (ListView) findViewById(R.id.schedules_itinerary_menudialog_listview);

        menuDialog.clearAnimation();

        listView.setVisibility(View.VISIBLE);
        menuDialog.setVisibility(View.VISIBLE);

        Animation mainLayoutAnimation = AnimationUtils.loadAnimation(this, R.anim.nexttoarrive_menudialog_scale_in);
        mainLayoutAnimation.setDuration(500);

        menuDialog.startAnimation(mainLayoutAnimation);
        listView.setVisibility(View.VISIBLE);
    }

    private void hideListView() {
        FrameLayout menuDialog = (FrameLayout) findViewById(R.id.schedules_itinerary_menudialog_mainlayout);
        menuDialog.clearAnimation();

        Animation mainLayOutAnimation = AnimationUtils.loadAnimation(this, R.anim.nexttoarrive_menudialog_scale_out);
        mainLayOutAnimation.setDuration(500);

        mainLayOutAnimation.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation arg0) {
            }

            @Override
            public void onAnimationRepeat(Animation arg0) {
            }

            @Override
            public void onAnimationEnd(Animation arg0) {
                ListView listView = (ListView) findViewById(R.id.schedules_itinerary_menudialog_listview);
                listView.setVisibility(View.GONE);
                menuRevealed = false;
            }
        });

        menuDialog.startAnimation(mainLayOutAnimation);
    }


    public void startEndSelectionSelected(View view) {
        this.inProcessOfStartDestinationFlow = true;

//        tripDataModel.clear();

        Intent stopSelectionIntent = null;

        stopSelectionIntent = new Intent(this, SchedulesRRStopSelectionActionBarActivity.class);
        stopSelectionIntent.putExtra(getString(R.string.regionalrail_stopselection_startordestination), "Start");
        startActivityForResult(stopSelectionIntent, getResources().getInteger(R.integer.nexttoarrive_stopselection_activityforresult_request_id));
    }

    public void selectStartSelected(View view) {
        Intent stopSelectionIntent = null;

        stopSelectionIntent = new Intent(this, SchedulesRRStopSelectionActionBarActivity.class);
        stopSelectionIntent.putExtra(getString(R.string.regionalrail_stopselection_startordestination), "Start");
        startActivityForResult(stopSelectionIntent, getResources().getInteger(R.integer.nexttoarrive_stopselection_activityforresult_request_id));
    }

    public void selectDestinationSelected(View view) {
        Intent stopSelectionIntent = null;

        stopSelectionIntent = new Intent(this, SchedulesRRStopSelectionActionBarActivity.class);
        stopSelectionIntent.putExtra(getString(R.string.regionalrail_stopselection_startordestination), "Destination");
        startActivityForResult(stopSelectionIntent, getResources().getInteger(R.integer.nexttoarrive_stopselection_activityforresult_request_id));
    }

    public void reverseStartEndSelected(View view) {
        schedulesRouteModel.reverseStartAndDestinationStops();
        SchedulesItinerary_ListViewItem_ArrayAdapter schedulesListViewItemArrayAdapter = (SchedulesItinerary_ListViewItem_ArrayAdapter) stickyList.getAdapter();
        schedulesListViewItemArrayAdapter.setRouteStartName(schedulesRouteModel.getRouteStartName());
        schedulesListViewItemArrayAdapter.setRouteEndName(schedulesRouteModel.getRouteEndName());

        checkTripStartAndDestinationForNextToArriveDataRequest();
    }

    private void checkTripStartAndDestinationForNextToArriveDataRequest() {
        Log.d(TAG, "check trip start and destination");
        // check if we have both the start and destination stops, if yes, fetch the data.
        if ((schedulesRouteModel.getRouteStartName() != null) &&
                !schedulesRouteModel.getRouteStartName().isEmpty() &&
                (schedulesRouteModel.getRouteEndName() != null) &&
                !schedulesRouteModel.getRouteEndName().isEmpty()) {

            ListView menuListView = (ListView) findViewById(R.id.schedules_itinerary_menudialog_listview);

            if (menuListView == null) {
                Log.d(TAG, "menu dialog listview is null");
            }

            if ((Schedules_Itinerary_MenuDialog_ListViewItem_ArrayAdapter) menuListView.getAdapter() == null) {
                Log.d(TAG, "menu dialogs adapter is null");
            }

            ((Schedules_Itinerary_MenuDialog_ListViewItem_ArrayAdapter) menuListView.getAdapter()).enableSaveAsFavorite();

            TextView endRouteNameTextView = (TextView) findViewById(R.id.schedules_itinerary_routedirection_textview);
            endRouteNameTextView.setText("To " + schedulesRouteModel.getRouteEndName());

            SchedulesFavoritesAndRecentlyViewedStore store = ObjectFactory.getInstance().getSchedulesFavoritesAndRecentlyViewedStore(this);

            SchedulesRecentlyViewedModel schedulesRecentlyViewedModel = new SchedulesRecentlyViewedModel();
            schedulesRecentlyViewedModel.setRouteId(schedulesRouteModel.getRouteId());
            schedulesRecentlyViewedModel.setRouteStartName(schedulesRouteModel.getRouteStartName());
            schedulesRecentlyViewedModel.setRouteStartStopId(schedulesRouteModel.getRouteStartStopId());
            schedulesRecentlyViewedModel.setRouteEndName(schedulesRouteModel.getRouteEndName());
            schedulesRecentlyViewedModel.setRouteEndStopId(schedulesRouteModel.getRouteEndStopId());

            SchedulesFavoriteModel schedulesFavoriteModel = new SchedulesFavoriteModel();
            schedulesFavoriteModel.setRouteId(schedulesRouteModel.getRouteId());
            schedulesFavoriteModel.setRouteStartStopId(schedulesRouteModel.getRouteStartStopId());
            schedulesFavoriteModel.setRouteStartName(schedulesRouteModel.getRouteStartName());
            schedulesFavoriteModel.setRouteEndStopId(schedulesRouteModel.getRouteEndStopId());
            schedulesFavoriteModel.setRouteEndName(schedulesRouteModel.getRouteEndName());

            // check if the selected route is already a favorite, then we allow the option of removing this
            // route from the favorites list.
            if (store.isFavorite(this.travelType.name(), schedulesFavoriteModel)) {
                ((Schedules_Itinerary_MenuDialog_ListViewItem_ArrayAdapter) menuListView.getAdapter()).enableRemoveSavedFavorite();
            }

            // check if this route is already stored as a favorite; if not store as a recent
            if (!store.isFavorite(this.travelType.name(), schedulesRecentlyViewedModel)) {
                store.addRecentlyViewed(this.travelType.name(), schedulesRecentlyViewedModel);
            }

            if (schedulesItineraryRefreshCountDownTimer != null) {
                schedulesItineraryRefreshCountDownTimer.cancel();
                schedulesItineraryRefreshCountDownTimer.start();
            } else {
                schedulesItineraryRefreshCountDownTimer = createScheduleItineraryRefreshCountDownTimer();
                schedulesItineraryRefreshCountDownTimer.start();
            }

            SchedulesDataModel schedulesDataModel = new SchedulesDataModel(this);

            schedulesDataModel.setRoute(schedulesRouteModel);
            schedulesDataModel.loadStartBasedTrips(travelType);
            schedulesDataModel.loadAndProcessEndStopsWithStartStops(travelType);

            ArrayList<TripObject> trips = schedulesDataModel.createFilteredTripsList(selectedTab);
            mAdapter.setTripObject(trips);
        }
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == getResources().getInteger(R.integer.nexttoarrive_stopselection_activityforresult_request_id)) {
            if (resultCode == RESULT_OK) {
                String stopName = data.getStringExtra("stop_name");
                String stopId = data.getStringExtra("stop_id");
                String selectionMode = data.getStringExtra("selection_mode");

                if (selectionMode.equals("destination")) {
                    schedulesRouteModel.setRouteEndStopId(stopId);
                    schedulesRouteModel.setRouteEndName(stopName);

                    if (inProcessOfStartDestinationFlow) {
                        inProcessOfStartDestinationFlow = false;
                    }
                } else {
                    if (schedulesRouteModel == null) {
                        Log.d(TAG, "schedulesRouteModel is null here, bad");
                    } else {
                        Log.d(TAG, "schedulesroutemodel is not null here, good");
                    }
                    schedulesRouteModel.setRouteStartStopId(stopId);
                    schedulesRouteModel.setRouteStartName(stopName);

                    if (inProcessOfStartDestinationFlow) {
                        Intent stopSelectionIntent = null;

                        stopSelectionIntent = new Intent(this, NextToArriveStopSelectionActionBarActivity.class);
                        stopSelectionIntent.putExtra(getString(R.string.regionalrail_stopselection_startordestination), "Destination");
                        startActivityForResult(stopSelectionIntent, getResources().getInteger(R.integer.nexttoarrive_stopselection_activityforresult_request_id));
                    }
                }

                SchedulesItinerary_ListViewItem_ArrayAdapter schedulesListViewItemArrayAdapter = (SchedulesItinerary_ListViewItem_ArrayAdapter) stickyList.getAdapter();
                schedulesListViewItemArrayAdapter.setRouteStartName(schedulesRouteModel.getRouteStartName());
                schedulesListViewItemArrayAdapter.setRouteEndName(schedulesRouteModel.getRouteEndName());

                checkTripStartAndDestinationForNextToArriveDataRequest();
            }
            if (resultCode == RESULT_CANCELED) {
                Log.d(TAG, "the result is canceled");
                //Write your code if there's no result
            }
        }
    }

    public void tabSelected(View view) {
        Log.d(TAG, "heard the tab selected");
        switch (view.getId()) {
            case R.id.schedules_itinerary_tab_now_button: {
                selectedTab = 0;
                mAdapter.setHeaderViewText("REMIANING TRIPS FOR TODAY");
                selectedNowTab();

                break;
            }
            case R.id.schedules_itinerary_tab_weekday_button: {
                selectedTab = 1;
                mAdapter.setHeaderViewText(tabLabels[selectedTab]);
                selectedWeekdayTab();

                break;
            }
            case R.id.schedules_itinerary_tab_sat_button: {
                selectedTab = 2;
                mAdapter.setHeaderViewText(tabLabels[selectedTab]);
                selectedSatTab();

                break;
            }
            case R.id.schedules_itinerary_tab_sun_button: {
                selectedTab = 3;
                mAdapter.setHeaderViewText(tabLabels[selectedTab]);
                selectedSunTab();

                break;
            }
            default: {
                Log.d("f", "not sure how we feel into this default for this switch");
            }
        }

        mAdapter.setSelectedTab(selectedTab);
        checkTripStartAndDestinationForNextToArriveDataRequest();
    }

    private void selectedNowTab() {
        // get the color from the looking array given the ordinal position of the route type
        String color = this.getResources().getStringArray(R.array.schedules_routeselection_routesheader_solid_colors)[travelType.ordinal()];

        Button tabNowButton = (Button) findViewById(R.id.schedules_itinerary_tab_now_button);
        Button tabWeekdayButton = (Button) findViewById(R.id.schedules_itinerary_tab_weekday_button);
        Button tabSatButton = (Button) findViewById(R.id.schedules_itinerary_tab_sat_button);
        Button tabSunButton = (Button) findViewById(R.id.schedules_itinerary_tab_sun_button);

        GradientDrawable nowTabButtonShapeDrawable = (GradientDrawable) tabNowButton.getBackground();
        GradientDrawable weekdayTabButtonShapeDrawable = (GradientDrawable) tabWeekdayButton.getBackground();
        GradientDrawable satTabButtonShapeDrawable = (GradientDrawable) tabSatButton.getBackground();
        GradientDrawable sunTabButtonShapeDrawable = (GradientDrawable) tabSunButton.getBackground();

        nowTabButtonShapeDrawable.setColor(Color.parseColor(color));
        tabNowButton.setTextColor(Color.WHITE);
        weekdayTabButtonShapeDrawable.setColor(Color.LTGRAY);
        tabWeekdayButton.setTextColor(Color.BLACK);
        satTabButtonShapeDrawable.setColor(Color.LTGRAY);
        tabSatButton.setTextColor(Color.BLACK);
        sunTabButtonShapeDrawable.setColor(Color.LTGRAY);
        tabSunButton.setTextColor(Color.BLACK);
    }

    private void selectedWeekdayTab() {
        Log.d(TAG, "weekday tab selected");
        // get the color from the looking array given the ordinal position of the route type
        String color = this.getResources().getStringArray(R.array.schedules_routeselection_routesheader_solid_colors)[travelType.ordinal()];

        Button tabNowButton = (Button) findViewById(R.id.schedules_itinerary_tab_now_button);
        Button tabWeekdayButton = (Button) findViewById(R.id.schedules_itinerary_tab_weekday_button);
        Button tabSatButton = (Button) findViewById(R.id.schedules_itinerary_tab_sat_button);
        Button tabSunButton = (Button) findViewById(R.id.schedules_itinerary_tab_sun_button);

        GradientDrawable nowTabButtonShapeDrawable = (GradientDrawable) tabNowButton.getBackground();
        GradientDrawable weekdayTabButtonShapeDrawable = (GradientDrawable) tabWeekdayButton.getBackground();
        GradientDrawable satTabButtonShapeDrawable = (GradientDrawable) tabSatButton.getBackground();
        GradientDrawable sunTabButtonShapeDrawable = (GradientDrawable) tabSunButton.getBackground();

        nowTabButtonShapeDrawable.setColor(Color.LTGRAY);
        tabNowButton.setTextColor(Color.BLACK);
        weekdayTabButtonShapeDrawable.setColor(Color.parseColor(color));
        tabWeekdayButton.setTextColor(Color.WHITE);
        satTabButtonShapeDrawable.setColor(Color.LTGRAY);
        tabSatButton.setTextColor(Color.BLACK);
        sunTabButtonShapeDrawable.setColor(Color.LTGRAY);
        tabSunButton.setTextColor(Color.BLACK);
    }

    private void selectedSatTab() {
        // get the color from the looking array given the ordinal position of the route type
        String color = this.getResources().getStringArray(R.array.schedules_routeselection_routesheader_solid_colors)[travelType.ordinal()];

        Button tabNowButton = (Button) findViewById(R.id.schedules_itinerary_tab_now_button);
        Button tabWeekdayButton = (Button) findViewById(R.id.schedules_itinerary_tab_weekday_button);
        Button tabSatButton = (Button) findViewById(R.id.schedules_itinerary_tab_sat_button);
        Button tabSunButton = (Button) findViewById(R.id.schedules_itinerary_tab_sun_button);

        GradientDrawable nowTabButtonShapeDrawable = (GradientDrawable) tabNowButton.getBackground();
        GradientDrawable weekdayTabButtonShapeDrawable = (GradientDrawable) tabWeekdayButton.getBackground();
        GradientDrawable satTabButtonShapeDrawable = (GradientDrawable) tabSatButton.getBackground();
        GradientDrawable sunTabButtonShapeDrawable = (GradientDrawable) tabSunButton.getBackground();

        nowTabButtonShapeDrawable.setColor(Color.LTGRAY);
        tabNowButton.setTextColor(Color.BLACK);
        weekdayTabButtonShapeDrawable.setColor(Color.LTGRAY);
        tabWeekdayButton.setTextColor(Color.BLACK);
        satTabButtonShapeDrawable.setColor(Color.parseColor(color));
        tabSatButton.setTextColor(Color.WHITE);
        sunTabButtonShapeDrawable.setColor(Color.LTGRAY);
        tabSunButton.setTextColor(Color.BLACK);
    }

    private void selectedSunTab() {
        // get the color from the looking array given the ordinal position of the route type
        String color = this.getResources().getStringArray(R.array.schedules_routeselection_routesheader_solid_colors)[travelType.ordinal()];

        Button tabNowButton = (Button) findViewById(R.id.schedules_itinerary_tab_now_button);
        Button tabWeekdayButton = (Button) findViewById(R.id.schedules_itinerary_tab_weekday_button);
        Button tabSatButton = (Button) findViewById(R.id.schedules_itinerary_tab_sat_button);
        Button tabSunButton = (Button) findViewById(R.id.schedules_itinerary_tab_sun_button);

        GradientDrawable nowTabButtonShapeDrawable = (GradientDrawable) tabNowButton.getBackground();
        GradientDrawable weekdayTabButtonShapeDrawable = (GradientDrawable) tabWeekdayButton.getBackground();
        GradientDrawable satTabButtonShapeDrawable = (GradientDrawable) tabSatButton.getBackground();
        GradientDrawable sunTabButtonShapeDrawable = (GradientDrawable) tabSunButton.getBackground();

        nowTabButtonShapeDrawable.setColor(Color.LTGRAY);
        tabNowButton.setTextColor(Color.BLACK);
        weekdayTabButtonShapeDrawable.setColor(Color.LTGRAY);
        tabWeekdayButton.setTextColor(Color.BLACK);
        satTabButtonShapeDrawable.setColor(Color.LTGRAY);
        tabSatButton.setTextColor(Color.BLACK);
        sunTabButtonShapeDrawable.setColor(Color.parseColor(color));
        tabSunButton.setTextColor(Color.WHITE);
    }


    @Override
    public void onConfigurationChanged(Configuration newConfig) {

        super.onConfigurationChanged(newConfig);
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
    }

    @Override
    public void onHeaderClick(StickyListHeadersListView l, View header,
                              int itemPosition, long headerId, boolean currentlySticky) {

    }

    @Override
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public void onStickyHeaderOffsetChanged(StickyListHeadersListView l, View header, int offset) {
        if (fadeHeader && Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            header.setAlpha(1 - (offset / (float) header.getMeasuredHeight()));
        }
    }

    @Override
    public void onStickyHeaderChanged(StickyListHeadersListView l, View header,
                                      int itemPosition, long headerId) {

    }

    @Override
    protected void onPause() {
        super.onPause();

        schedulesItineraryRefreshCountDownTimer.cancel();
    }

    @Override
    protected void onStart() {
        super.onStart();

        schedulesItineraryRefreshCountDownTimer = createScheduleItineraryRefreshCountDownTimer();
        if ((schedulesRouteModel.getRouteStartName() != null) &&
                !schedulesRouteModel.getRouteStartName().isEmpty() &&
                (schedulesRouteModel.getRouteEndName() != null) &&
                !schedulesRouteModel.getRouteEndName().isEmpty()) {
            schedulesItineraryRefreshCountDownTimer.start();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();

        schedulesItineraryRefreshCountDownTimer = null;
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        v.setOnTouchListener(null);
        return false;
    }

    private CountDownTimer createScheduleItineraryRefreshCountDownTimer() {
        return new CountDownTimer(20000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {

//                ((Schedules_Itinerary_MenuDialog_ListViewItem_ArrayAdapter)menuDialogListView.getAdapter()).setNextRefreshInSecondsValue(millisUntilFinished/1000);
            }

            @Override
            public void onFinish() {

                checkTripStartAndDestinationForNextToArriveDataRequest();
            }
        };
    }
}