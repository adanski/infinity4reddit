package ml.docilealligator.infinityforreddit.Activity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestManager;
import com.google.android.material.appbar.AppBarLayout;

import java.util.ArrayList;

import javax.inject.Inject;
import javax.inject.Named;

import butterknife.BindView;
import butterknife.ButterKnife;
import ml.docilealligator.infinityforreddit.Adapter.SubredditMultiselectionRecyclerViewAdapter;
import ml.docilealligator.infinityforreddit.AsyncTask.GetCurrentAccountAsyncTask;
import ml.docilealligator.infinityforreddit.CustomTheme.CustomThemeWrapper;
import ml.docilealligator.infinityforreddit.Infinity;
import ml.docilealligator.infinityforreddit.R;
import ml.docilealligator.infinityforreddit.RedditDataRoomDatabase;
import ml.docilealligator.infinityforreddit.SubredditDatabase.SubredditData;
import ml.docilealligator.infinityforreddit.SubredditWithSelection;
import ml.docilealligator.infinityforreddit.SubscribedSubredditDatabase.SubscribedSubredditViewModel;
import retrofit2.Retrofit;

public class SubredditMultiselectionActivity extends BaseActivity {

    static final String EXTRA_SELECTED_SUBSCRIBED_SUBREDDITS = "ESS";
    static final String EXTRA_SELECTED_OTHER_SUBREDDITS = "EOSS";
    static final String EXTRA_RETURN_SELECTED_SUBSCRIBED_SUBREDDITS = "ERSSS";
    static final String EXTRA_RETURN_SUBSCRIBED_OTHER_SUBREDDITS = "EROSS";

    private static final int SUBREDDIT_SEARCH_REQUEST_CODE = 1;
    private static final String NULL_ACCESS_TOKEN_STATE = "NATS";
    private static final String ACCESS_TOKEN_STATE = "ATS";
    private static final String ACCOUNT_NAME_STATE = "ANS";
    private static final String SELECTED_SUBSCRIBED_SUBREDDITS_STATE = "SSSS";
    private static final String SELECTED_OTHER_SUBREDDITS_STATE = "SOSS";

    @BindView(R.id.coordinator_layout_subreddits_multiselection_activity)
    CoordinatorLayout mCoordinatorLayout;
    @BindView(R.id.appbar_layout_subreddits_multiselection_activity)
    AppBarLayout mAppBarLayout;
    @BindView(R.id.toolbar_subscribed_subreddits_multiselection_activity)
    Toolbar mToolbar;
    @BindView(R.id.swipe_refresh_layout_subscribed_subscribed_subreddits_multiselection_activity)
    SwipeRefreshLayout mSwipeRefreshLayout;
    @BindView(R.id.recycler_view_subscribed_subscribed_subreddits_multiselection_activity)
    RecyclerView mRecyclerView;
    @BindView(R.id.no_subscriptions_linear_layout_subscribed_subreddits_multiselection_activity)
    LinearLayout mLinearLayout;
    @BindView(R.id.no_subscriptions_image_view_subscribed_subreddits_multiselection_activity)
    ImageView mImageView;
    @BindView(R.id.error_text_view_subscribed_subreddits_multiselection_activity)
    TextView mErrorTextView;
    @Inject
    @Named("oauth")
    Retrofit mOauthRetrofit;
    @Inject
    RedditDataRoomDatabase mRedditDataRoomDatabase;
    @Inject
    @Named("default")
    SharedPreferences mSharedPreferences;
    @Inject
    CustomThemeWrapper mCustomThemeWrapper;
    public SubscribedSubredditViewModel mSubscribedSubredditViewModel;
    private boolean mNullAccessToken = false;
    private String mAccessToken;
    private String mAccountName;
    private SubredditMultiselectionRecyclerViewAdapter mAdapter;
    private RequestManager mGlide;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ((Infinity) getApplication()).getAppComponent().inject(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_subscribed_subreddits_multiselection);

        ButterKnife.bind(this);

        applyCustomTheme();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Window window = getWindow();

            if (isChangeStatusBarIconColor()) {
                addOnOffsetChangedListener(mAppBarLayout);
            }

            if (isImmersiveInterface()) {
                window.setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
                adjustToolbar(mToolbar);

                int navBarHeight = getNavBarHeight();
                if (navBarHeight > 0) {
                    mRecyclerView.setPadding(0, 0, 0, navBarHeight);
                }
            }
        }

        setSupportActionBar(mToolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        mGlide = Glide.with(this);

        mSwipeRefreshLayout.setEnabled(false);

        if (savedInstanceState != null) {
            mNullAccessToken = savedInstanceState.getBoolean(NULL_ACCESS_TOKEN_STATE);
            mAccessToken = savedInstanceState.getString(ACCESS_TOKEN_STATE);
            mAccountName = savedInstanceState.getString(ACCOUNT_NAME_STATE);

            if (!mNullAccessToken && mAccountName == null) {
                getCurrentAccountAndBindView(savedInstanceState.getParcelableArrayList(SELECTED_SUBSCRIBED_SUBREDDITS_STATE),
                        savedInstanceState.getParcelableArrayList(SELECTED_OTHER_SUBREDDITS_STATE));
            } else {
                bindView(savedInstanceState.getParcelableArrayList(SELECTED_SUBSCRIBED_SUBREDDITS_STATE),
                        savedInstanceState.getParcelableArrayList(SELECTED_OTHER_SUBREDDITS_STATE));
            }
        } else {
            getCurrentAccountAndBindView(getIntent().getParcelableArrayListExtra(EXTRA_SELECTED_SUBSCRIBED_SUBREDDITS),
                    getIntent().getParcelableArrayListExtra(EXTRA_SELECTED_OTHER_SUBREDDITS));
        }
    }

    private void getCurrentAccountAndBindView(ArrayList<SubredditWithSelection> selectedSubscribedSubreddits,
                                              ArrayList<SubredditWithSelection> otherSubreddits) {
        new GetCurrentAccountAsyncTask(mRedditDataRoomDatabase.accountDao(), account -> {
            if (account == null) {
                mNullAccessToken = true;
                Toast.makeText(this, R.string.logged_out, Toast.LENGTH_SHORT).show();
                finish();
            } else {
                mAccessToken = account.getAccessToken();
                mAccountName = account.getUsername();
                bindView(selectedSubscribedSubreddits, otherSubreddits);
            }
        }).execute();
    }

    private void bindView(ArrayList<SubredditWithSelection> selectedSubscribedSubreddits,
                          ArrayList<SubredditWithSelection> otherSubreddits) {
        mRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        mAdapter = new SubredditMultiselectionRecyclerViewAdapter(this, mCustomThemeWrapper,
                selectedSubscribedSubreddits, otherSubreddits);
        mRecyclerView.setAdapter(mAdapter);

        mSubscribedSubredditViewModel = new ViewModelProvider(this,
                new SubscribedSubredditViewModel.Factory(getApplication(), mRedditDataRoomDatabase, mAccountName))
                .get(SubscribedSubredditViewModel.class);
        mSubscribedSubredditViewModel.getAllSubscribedSubreddits().observe(this, subscribedSubredditData -> {
            mSwipeRefreshLayout.setRefreshing(false);
            if (subscribedSubredditData == null || subscribedSubredditData.size() == 0) {
                mRecyclerView.setVisibility(View.GONE);
                mLinearLayout.setVisibility(View.VISIBLE);
                mGlide.load(R.drawable.error_image).into(mImageView);
            } else {
                mLinearLayout.setVisibility(View.GONE);
                mRecyclerView.setVisibility(View.VISIBLE);
                mGlide.clear(mImageView);
            }

            mAdapter.setSubscribedSubreddits(subscribedSubredditData);
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.subreddit_multiselection_activity, menu);
        applyMenuItemTheme(menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
            case R.id.action_save_subreddit_multiselection_activity:
                if (mAdapter != null) {
                    Intent returnIntent = new Intent();
                    returnIntent.putParcelableArrayListExtra(EXTRA_RETURN_SELECTED_SUBSCRIBED_SUBREDDITS,
                            mAdapter.getAllSelectedSubscribedSubreddits());
                    returnIntent.putParcelableArrayListExtra(EXTRA_RETURN_SUBSCRIBED_OTHER_SUBREDDITS,
                            mAdapter.getAllSelectedOtherSubreddits());
                    setResult(RESULT_OK, returnIntent);
                }
                finish();
                return true;
            case R.id.action_search_subreddit_multiselection_activity:
                Intent intent = new Intent(this, SearchActivity.class);
                intent.putExtra(SearchActivity.EXTRA_SEARCH_ONLY_SUBREDDITS, true);
                startActivityForResult(intent, SUBREDDIT_SEARCH_REQUEST_CODE);
        }

        return false;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == SUBREDDIT_SEARCH_REQUEST_CODE && resultCode == RESULT_OK && data != null
                && mAdapter != null) {
            String subredditName = data.getStringExtra(SearchActivity.EXTRA_RETURN_SUBREDDIT_NAME);
            String subredditIconUrl = data.getStringExtra(SearchActivity.EXTRA_RETURN_SUBREDDIT_ICON_URL);
            mAdapter.addOtherSubreddit(new SubredditData("-1", subredditName, subredditIconUrl,
                    null, null, null, 0));
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(NULL_ACCESS_TOKEN_STATE, mNullAccessToken);
        outState.putString(ACCESS_TOKEN_STATE, mAccessToken);
        outState.putString(ACCOUNT_NAME_STATE, mAccountName);
        outState.putParcelableArrayList(SELECTED_SUBSCRIBED_SUBREDDITS_STATE, mAdapter.getAllSelectedSubscribedSubreddits());
        outState.putParcelableArrayList(SELECTED_OTHER_SUBREDDITS_STATE, mAdapter.getAllOtherSubreddits());
    }

    @Override
    public SharedPreferences getSharedPreferences() {
        return mSharedPreferences;
    }

    @Override
    protected CustomThemeWrapper getCustomThemeWrapper() {
        return mCustomThemeWrapper;
    }

    @Override
    protected void applyCustomTheme() {
        mCoordinatorLayout.setBackgroundColor(mCustomThemeWrapper.getBackgroundColor());
        applyAppBarLayoutAndToolbarTheme(mAppBarLayout, mToolbar);
        mErrorTextView.setTextColor(mCustomThemeWrapper.getSecondaryTextColor());
    }
}
