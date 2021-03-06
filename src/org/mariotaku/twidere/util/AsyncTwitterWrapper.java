/*
 *				Twidere - Twitter client for Android
 * 
 * Copyright (C) 2012 Mariotaku Lee <mariotaku.lee@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.mariotaku.twidere.util;

import static android.text.TextUtils.isEmpty;
import static org.mariotaku.twidere.provider.TweetStore.STATUSES_URIS;
import static org.mariotaku.twidere.util.Utils.appendQueryParameters;
import static org.mariotaku.twidere.util.Utils.getAccountScreenName;
import static org.mariotaku.twidere.util.Utils.getActivatedAccountIds;
import static org.mariotaku.twidere.util.Utils.getAllStatusesIds;
import static org.mariotaku.twidere.util.Utils.getImagePathFromUri;
import static org.mariotaku.twidere.util.Utils.getImageUploadStatus;
import static org.mariotaku.twidere.util.Utils.getNewestMessageIdsFromDatabase;
import static org.mariotaku.twidere.util.Utils.getNewestStatusIdsFromDatabase;
import static org.mariotaku.twidere.util.Utils.getStatusIdsInDatabase;
import static org.mariotaku.twidere.util.Utils.getTwitterInstance;
import static org.mariotaku.twidere.util.Utils.makeDirectMessageContentValues;
import static org.mariotaku.twidere.util.Utils.makeStatusContentValues;
import static org.mariotaku.twidere.util.Utils.makeTrendsContentValues;
import static org.mariotaku.twidere.util.Utils.parseString;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.mariotaku.twidere.R;
import org.mariotaku.twidere.app.TwidereApplication;
import org.mariotaku.twidere.model.ListResponse;
import org.mariotaku.twidere.model.ParcelableLocation;
import org.mariotaku.twidere.model.SingleResponse;
import org.mariotaku.twidere.provider.TweetStore;
import org.mariotaku.twidere.provider.TweetStore.CachedHashtags;
import org.mariotaku.twidere.provider.TweetStore.CachedTrends;
import org.mariotaku.twidere.provider.TweetStore.CachedUsers;
import org.mariotaku.twidere.provider.TweetStore.DirectMessages;
import org.mariotaku.twidere.provider.TweetStore.Drafts;
import org.mariotaku.twidere.provider.TweetStore.Mentions;
import org.mariotaku.twidere.provider.TweetStore.Statuses;

import twitter4j.DirectMessage;
import twitter4j.Paging;
import twitter4j.ResponseList;
import twitter4j.StatusUpdate;
import twitter4j.Trends;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.User;
import twitter4j.UserList;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;
import android.widget.Toast;

import com.twitter.Extractor;
import com.twitter.Validator;

import edu.ucdavis.earlybird.ProfilingUtil;

public class AsyncTwitterWrapper extends TwitterWrapper {

	private static AsyncTwitterWrapper sInstance;

	private final Context mContext;
	private final AsyncTaskManager mAsyncTaskManager;
	private final SharedPreferences mPreferences;
	private final NotificationManager mNotificationManager;
	private final ContentResolver mResolver;
	private final Resources mResources;

	private final boolean large_profile_image;

	private int mGetHomeTimelineTaskId, mGetMentionsTaskId;
	private int mGetReceivedDirectMessagesTaskId, mGetSentDirectMessagesTaskId;
	private int mGetLocalTrendsTaskId;

	public AsyncTwitterWrapper(final Context context) {
		mContext = context;
		mNotificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
		mAsyncTaskManager = TwidereApplication.getInstance(context).getAsyncTaskManager();
		mPreferences = context.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
		mResolver = context.getContentResolver();
		mResources = context.getResources();
		large_profile_image = context.getResources().getBoolean(R.bool.hires_profile_image);
	}

	public int addUserListMember(final long account_id, final int list_id, final long user_id, final String screen_name) {
		final AddUserListMemberTask task = new AddUserListMemberTask(account_id, list_id, user_id, screen_name);
		return mAsyncTaskManager.add(task, true);
	}

	public void clearNotification(final int id) {
		final Uri uri = TweetStore.CONTENT_URI_NOTOFICATIONS.buildUpon().appendPath(String.valueOf(id)).build();
		mResolver.delete(uri, null, null);
	}

	public int createBlockAsync(final long account_id, final long user_id) {
		final CreateBlockTask task = new CreateBlockTask(account_id, user_id);
		return mAsyncTaskManager.add(task, true);
	}

	public int createFavoriteAsync(final long account_id, final long status_id) {
		final CreateFavoriteTask task = new CreateFavoriteTask(account_id, status_id);
		return mAsyncTaskManager.add(task, true);
	}

	public int createFriendship(final long account_id, final long user_id) {
		final CreateFriendshipTask task = new CreateFriendshipTask(account_id, user_id);
		return mAsyncTaskManager.add(task, true);
	}

	public int createMultiBlock(final long account_id, final long[] user_ids) {
		final CreateMultiBlockTask task = new CreateMultiBlockTask(account_id, user_ids);
		return mAsyncTaskManager.add(task, true);
	}

	public int createUserList(final long account_id, final String list_name, final boolean is_public,
			final String description) {
		final CreateUserListTask task = new CreateUserListTask(account_id, list_name, is_public, description);
		return mAsyncTaskManager.add(task, true);
	}

	public int createUserListSubscription(final long account_id, final int list_id) {
		final CreateUserListSubscriptionTask task = new CreateUserListSubscriptionTask(account_id, list_id);
		return mAsyncTaskManager.add(task, true);
	}

	public int deleteUserListMember(final long account_id, final int list_id, final long user_id) {
		final DeleteUserListMemberTask task = new DeleteUserListMemberTask(account_id, list_id, user_id);
		return mAsyncTaskManager.add(task, true);
	}

	public int destroyBlock(final long account_id, final long user_id) {
		final DestroyBlockTask task = new DestroyBlockTask(account_id, user_id);
		return mAsyncTaskManager.add(task, true);
	}

	public int destroyDirectMessage(final long account_id, final long message_id) {
		final DestroyDirectMessageTask task = new DestroyDirectMessageTask(account_id, message_id);
		return mAsyncTaskManager.add(task, true);
	}

	public int destroyFavorite(final long account_id, final long status_id) {
		final DestroyFavoriteTask task = new DestroyFavoriteTask(account_id, status_id);
		return mAsyncTaskManager.add(task, true);
	}

	public int destroyFriendship(final long account_id, final long user_id) {
		final DestroyFriendshipTask task = new DestroyFriendshipTask(account_id, user_id);
		return mAsyncTaskManager.add(task, true);
	}

	public int destroyStatus(final long account_id, final long status_id) {
		final DestroyStatusTask task = new DestroyStatusTask(account_id, status_id);
		return mAsyncTaskManager.add(task, true);
	}

	public int destroyUserList(final long account_id, final int list_id) {
		final DestroyUserListTask task = new DestroyUserListTask(account_id, list_id);
		return mAsyncTaskManager.add(task, true);
	}

	public int destroyUserListSubscription(final long account_id, final int list_id) {
		final DestroyUserListSubscriptionTask task = new DestroyUserListSubscriptionTask(account_id, list_id);
		return mAsyncTaskManager.add(task, true);
	}

	public int getHomeTimeline(final long[] account_ids, final long[] max_ids, final long[] since_ids) {
		mAsyncTaskManager.cancel(mGetHomeTimelineTaskId);
		final GetHomeTimelineTask task = new GetHomeTimelineTask(account_ids, max_ids, since_ids);
		return mGetHomeTimelineTaskId = mAsyncTaskManager.add(task, true);
	}

	public int getLocalTrends(final long account_id, final int woeid) {
		mAsyncTaskManager.cancel(mGetLocalTrendsTaskId);
		final GetLocalTrendsTask task = new GetLocalTrendsTask(account_id, woeid);
		return mGetLocalTrendsTaskId = mAsyncTaskManager.add(task, true);
	}

	public int getMentions(final long[] account_ids, final long[] max_ids, final long[] since_ids) {
		mAsyncTaskManager.cancel(mGetMentionsTaskId);
		final GetMentionsTask task = new GetMentionsTask(account_ids, max_ids, since_ids);
		return mGetMentionsTaskId = mAsyncTaskManager.add(task, true);
	}

	public int getReceivedDirectMessages(final long[] account_ids, final long[] max_ids, final long[] since_ids) {
		mAsyncTaskManager.cancel(mGetReceivedDirectMessagesTaskId);
		final GetReceivedDirectMessagesTask task = new GetReceivedDirectMessagesTask(account_ids, max_ids, since_ids);
		return mGetReceivedDirectMessagesTaskId = mAsyncTaskManager.add(task, true);
	}

	public int getSentDirectMessages(final long[] account_ids, final long[] max_ids, final long[] since_ids) {
		mAsyncTaskManager.cancel(mGetSentDirectMessagesTaskId);
		final GetSentDirectMessagesTask task = new GetSentDirectMessagesTask(account_ids, max_ids, since_ids);
		return mGetSentDirectMessagesTaskId = mAsyncTaskManager.add(task, true);
	}

	public boolean hasActivatedTask() {
		return mAsyncTaskManager.hasRunningTask();
	}

	public boolean isHomeTimelineRefreshing() {
		return mAsyncTaskManager.hasRunningTasksForTag(TASK_TAG_GET_HOME_TIMELINE)
				|| mAsyncTaskManager.hasRunningTasksForTag(TASK_TAG_STORE_HOME_TIMELINE);
	}

	public boolean isLocalTrendsRefreshing() {
		return mAsyncTaskManager.hasRunningTasksForTag(TASK_TAG_GET_TRENDS)
				|| mAsyncTaskManager.hasRunningTasksForTag(TASK_TAG_STORE_TRENDS);
	}

	public boolean isMentionsRefreshing() {
		return mAsyncTaskManager.hasRunningTasksForTag(TASK_TAG_GET_MENTIONS)
				|| mAsyncTaskManager.hasRunningTasksForTag(TASK_TAG_STORE_MENTIONS);
	}

	public boolean isReceivedDirectMessagesRefreshing() {
		return mAsyncTaskManager.hasRunningTasksForTag(TASK_TAG_GET_RECEIVED_DIRECT_MESSAGES)
				|| mAsyncTaskManager.hasRunningTasksForTag(TASK_TAG_STORE_RECEIVED_DIRECT_MESSAGES);
	}

	public boolean isSentDirectMessagesRefreshing() {
		return mAsyncTaskManager.hasRunningTasksForTag(TASK_TAG_GET_SENT_DIRECT_MESSAGES)
				|| mAsyncTaskManager.hasRunningTasksForTag(TASK_TAG_STORE_SENT_DIRECT_MESSAGES);
	}

	public int refreshAll() {
		final long[] account_ids = getActivatedAccountIds(mContext);
		if (mPreferences.getBoolean(PREFERENCE_KEY_HOME_REFRESH_MENTIONS, false)) {
			final long[] since_ids = getNewestStatusIdsFromDatabase(mContext, Mentions.CONTENT_URI);
			getMentions(account_ids, null, since_ids);
		}
		if (mPreferences.getBoolean(PREFERENCE_KEY_HOME_REFRESH_DIRECT_MESSAGES, false)) {
			final long[] since_ids = getNewestMessageIdsFromDatabase(mContext, DirectMessages.Inbox.CONTENT_URI);
			getReceivedDirectMessages(account_ids, null, since_ids);
			getSentDirectMessages(account_ids, null, null);
		}
		final long[] since_ids = getNewestStatusIdsFromDatabase(mContext, Statuses.CONTENT_URI);
		return getHomeTimeline(account_ids, null, since_ids);
	}

	public int reportMultiSpam(final long account_id, final long[] user_ids) {
		final ReportMultiSpamTask task = new ReportMultiSpamTask(account_id, user_ids);
		return mAsyncTaskManager.add(task, true);
	}

	public int reportSpam(final long account_id, final long user_id) {
		final ReportSpamTask task = new ReportSpamTask(account_id, user_id);
		return mAsyncTaskManager.add(task, true);
	}

	public int retweetStatus(final long account_id, final long status_id) {
		final RetweetStatusTask task = new RetweetStatusTask(account_id, status_id);
		return mAsyncTaskManager.add(task, true);
	}

	public int sendDirectMessage(final long account_id, final String screen_name, final long user_id,
			final String message) {
		final SendDirectMessageTask task = new SendDirectMessageTask(account_id, screen_name, user_id, message);
		return mAsyncTaskManager.add(task, true);
	}

	public int updateProfile(final long account_id, final String name, final String url, final String location,
			final String description) {
		final UpdateProfileTask task = new UpdateProfileTask(mContext, mAsyncTaskManager, account_id, name, url,
				location, description);
		return mAsyncTaskManager.add(task, true);
	}

	public int updateProfileBannerImage(final long account_id, final Uri image_uri, final boolean delete_image) {
		final UpdateProfileBannerImageTask task = new UpdateProfileBannerImageTask(mContext, mAsyncTaskManager,
				account_id, image_uri, delete_image);
		return mAsyncTaskManager.add(task, true);
	}

	public int updateProfileImage(final long account_id, final Uri image_uri, final boolean delete_image) {
		final UpdateProfileImageTask task = new UpdateProfileImageTask(mContext, mAsyncTaskManager, account_id,
				image_uri, delete_image);
		return mAsyncTaskManager.add(task, true);
	}

	public int updateStatus(final long[] account_ids, final String content, final ParcelableLocation location,
			final Uri image_uri, final long in_reply_to, final boolean is_possibly_sensitive, final boolean delete_image) {
		final UpdateStatusTask task = new UpdateStatusTask(account_ids, content, location, image_uri, in_reply_to,
				is_possibly_sensitive, delete_image);
		return mAsyncTaskManager.add(task, true);
	}

	public int updateUserListDetails(final long account_id, final int list_id, final boolean is_public,
			final String name, final String description) {
		final UpdateUserListDetailsTask task = new UpdateUserListDetailsTask(account_id, list_id, is_public, name,
				description);
		return mAsyncTaskManager.add(task, true);
	}

	private Notification buildNotification(final String title, final String message, final int icon,
			final Intent content_intent, final Intent delete_intent) {
		final NotificationCompat.Builder builder = new NotificationCompat.Builder(mContext);
		builder.setTicker(message);
		builder.setContentTitle(title);
		builder.setContentText(message);
		builder.setAutoCancel(true);
		builder.setWhen(System.currentTimeMillis());
		builder.setSmallIcon(icon);
		if (delete_intent != null) {
			builder.setDeleteIntent(PendingIntent.getBroadcast(mContext, 0, delete_intent,
					PendingIntent.FLAG_UPDATE_CURRENT));
		}
		if (content_intent != null) {
			builder.setContentIntent(PendingIntent.getActivity(mContext, 0, content_intent,
					PendingIntent.FLAG_UPDATE_CURRENT));
		}
		int defaults = 0;
		if (mPreferences.getBoolean(PREFERENCE_KEY_NOTIFICATION_HAVE_SOUND, false)) {
			final Uri def_ringtone = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
			final String path = mPreferences.getString(PREFERENCE_KEY_NOTIFICATION_RINGTONE, "");
			builder.setSound(isEmpty(path) ? def_ringtone : Uri.parse(path), Notification.STREAM_DEFAULT);
		}
		if (mPreferences.getBoolean(PREFERENCE_KEY_NOTIFICATION_HAVE_VIBRATION, false)) {
			defaults |= Notification.DEFAULT_VIBRATE;
		}
		if (mPreferences.getBoolean(PREFERENCE_KEY_NOTIFICATION_HAVE_LIGHTS, false)) {
			final int color_def = mResources.getColor(R.color.holo_blue_dark);
			final int color = mPreferences.getInt(PREFERENCE_KEY_NOTIFICATION_LIGHT_COLOR, color_def);
			builder.setLights(color, 1000, 2000);
		}
		builder.setDefaults(defaults);
		return builder.build();
	}

	private void showErrorToast(final int action_res, final Exception e, final boolean long_message) {
		Utils.showErrorToast(mContext, mContext.getString(action_res), e, long_message);
	}

	public static AsyncTwitterWrapper getInstance(final Context context) {
		if (sInstance != null) return sInstance;
		return sInstance = new AsyncTwitterWrapper(context);
	}

	public static class UpdateProfileBannerImageTask extends ManagedAsyncTask<Void, Void, SingleResponse<Boolean>> {

		private final long account_id;
		private final Uri image_uri;
		private final boolean delete_image;
		private final Context mContext;

		public UpdateProfileBannerImageTask(final Context context, final AsyncTaskManager manager,
				final long account_id, final Uri image_uri, final boolean delete_image) {
			super(context, manager);
			mContext = context;
			this.account_id = account_id;
			this.image_uri = image_uri;
			this.delete_image = delete_image;
		}

		@Override
		protected SingleResponse<Boolean> doInBackground(final Void... params) {
			return TwitterWrapper.updateProfileBannerImage(mContext, account_id, image_uri, delete_image);
		}

		@Override
		protected void onPostExecute(final SingleResponse<Boolean> result) {
			if (result != null && result.data != null && result.data) {
				Toast.makeText(mContext, R.string.profile_banner_image_update_successful, Toast.LENGTH_SHORT).show();
			} else {
				Utils.showErrorToast(mContext, R.string.updating_profile_banner_image, result.exception, true);
			}
			final Intent intent = new Intent(BROADCAST_PROFILE_BANNER_UPDATED);
			intent.putExtra(INTENT_KEY_USER_ID, account_id);
			intent.putExtra(INTENT_KEY_SUCCEED, result != null && result.data != null);
			mContext.sendBroadcast(intent);
			super.onPostExecute(result);
		}

	}

	public static class UpdateProfileImageTask extends ManagedAsyncTask<Void, Void, SingleResponse<User>> {

		private final long account_id;
		private final Uri image_uri;
		private final boolean delete_image;
		private final Context context;

		public UpdateProfileImageTask(final Context context, final AsyncTaskManager manager, final long account_id,
				final Uri image_uri, final boolean delete_image) {
			super(context, manager);
			this.context = context;
			this.account_id = account_id;
			this.image_uri = image_uri;
			this.delete_image = delete_image;
		}

		@Override
		protected SingleResponse<User> doInBackground(final Void... params) {
			return TwitterWrapper.updateProfileImage(context, account_id, image_uri, delete_image);
		}

		@Override
		protected void onPostExecute(final SingleResponse<User> result) {
			if (result != null && result.data != null) {
				Toast.makeText(context, R.string.profile_image_update_successful, Toast.LENGTH_SHORT).show();
			} else {
				Utils.showErrorToast(context, R.string.updating_profile_image, result.exception, true);
			}
			final Intent intent = new Intent(BROADCAST_PROFILE_UPDATED);
			intent.putExtra(INTENT_KEY_USER_ID, account_id);
			intent.putExtra(INTENT_KEY_SUCCEED, result != null && result.data != null);
			context.sendBroadcast(intent);
			super.onPostExecute(result);
		}

	}

	public static class UpdateProfileTask extends ManagedAsyncTask<Void, Void, SingleResponse<User>> {

		private final long account_id;
		private final String name, url, location, description;
		private final Context context;

		public UpdateProfileTask(final Context context, final AsyncTaskManager manager, final long account_id,
				final String name, final String url, final String location, final String description) {
			super(context, manager);
			this.context = context;
			this.account_id = account_id;
			this.name = name;
			this.url = url;
			this.location = location;
			this.description = description;
		}

		@Override
		protected SingleResponse<User> doInBackground(final Void... params) {
			return updateProfile(context, account_id, name, url, location, description);
		}

		@Override
		protected void onPostExecute(final SingleResponse<User> result) {
			if (result != null && result.data != null) {
				Toast.makeText(context, R.string.profile_update_successful, Toast.LENGTH_SHORT).show();
			} else {
				Utils.showErrorToast(context, context.getString(R.string.updating_profile), result.exception, true);
			}
			final Intent intent = new Intent(BROADCAST_PROFILE_IMAGE_UPDATED);
			intent.putExtra(INTENT_KEY_USER_ID, account_id);
			intent.putExtra(INTENT_KEY_SUCCEED, result != null && result.data != null);
			context.sendBroadcast(intent);
			super.onPostExecute(result);
		}

	}

	class AddUserListMemberTask extends ManagedAsyncTask<Void, Void, SingleResponse<UserList>> {

		private final long account_id, user_id;
		private final int list_id;
		private final String screen_name;

		public AddUserListMemberTask(final long account_id, final int list_id, final long user_id,
				final String screen_name) {
			super(mContext, mAsyncTaskManager);
			this.account_id = account_id;
			this.list_id = list_id;
			this.user_id = user_id;
			this.screen_name = screen_name;
		}

		@Override
		protected SingleResponse<UserList> doInBackground(final Void... params) {

			final Twitter twitter = getTwitterInstance(mContext, account_id, false);
			if (twitter != null) {
				try {
					if (user_id > 0) {
						final UserList list = twitter.addUserListMember(list_id, user_id);
						return new TwitterSingleResponse<UserList>(account_id, list, null);
					} else if (screen_name != null) {
						final User user = twitter.showUser(screen_name);
						if (user != null && user.getId() > 0) {
							final UserList list = twitter.addUserListMember(list_id, user.getId());
							return new TwitterSingleResponse<UserList>(account_id, list, null);
						}
					}
				} catch (final TwitterException e) {
					return new TwitterSingleResponse<UserList>(account_id, null, e);
				}
			}
			return new TwitterSingleResponse<UserList>(account_id, null, null);
		}

		@Override
		protected void onPostExecute(final SingleResponse<UserList> result) {
			final boolean succeed = result != null && result.data != null && result.data.getId() > 0;
			if (succeed) {
				Toast.makeText(mContext, R.string.add_successful, Toast.LENGTH_SHORT).show();
			} else {
				showErrorToast(R.string.adding_member, result.exception, true);
			}
			final Intent intent = new Intent(BROADCAST_USER_LIST_MEMBER_DELETED);
			intent.putExtra(INTENT_KEY_USER_ID, user_id);
			intent.putExtra(INTENT_KEY_LIST_ID, list_id);
			intent.putExtra(INTENT_KEY_SUCCEED, succeed);
			mContext.sendBroadcast(intent);
			super.onPostExecute(result);
		}

	}

	class CreateBlockTask extends ManagedAsyncTask<Void, Void, SingleResponse<User>> {

		private final long account_id, user_id;

		public CreateBlockTask(final long account_id, final long user_id) {
			super(mContext, mAsyncTaskManager);
			this.account_id = account_id;
			this.user_id = user_id;
		}

		@Override
		protected SingleResponse<User> doInBackground(final Void... params) {

			final Twitter twitter = getTwitterInstance(mContext, account_id, false);
			if (twitter != null) {
				try {
					final User user = twitter.createBlock(user_id);
					return new TwitterSingleResponse<User>(account_id, user, null);
				} catch (final TwitterException e) {
					return new TwitterSingleResponse<User>(account_id, null, e);
				}
			}
			return new TwitterSingleResponse<User>(account_id, null, null);
		}

		@Override
		protected void onPostExecute(final SingleResponse<User> result) {
			if (result != null && result.data != null && result.data.getId() > 0) {
				for (final Uri uri : STATUSES_URIS) {
					final String where = Statuses.ACCOUNT_ID + " = " + account_id + " AND " + Statuses.USER_ID + " = "
							+ user_id;
					mResolver.delete(uri, where, null);

				}
				// I bet you don't want to see this user in your auto complete
				// list.
				final String where = CachedUsers.USER_ID + " = " + user_id;
				mResolver.delete(CachedUsers.CONTENT_URI, where, null);
				Toast.makeText(mContext, R.string.user_blocked, Toast.LENGTH_SHORT).show();
			} else {
				showErrorToast(R.string.blocking, result.exception, true);
			}
			final Intent intent = new Intent(BROADCAST_BLOCKSTATE_CHANGED);
			intent.putExtra(INTENT_KEY_USER_ID, user_id);
			intent.putExtra(INTENT_KEY_SUCCEED, result != null && result.data != null);
			mContext.sendBroadcast(intent);
			super.onPostExecute(result);
		}

	}

	class CreateFavoriteTask extends ManagedAsyncTask<Void, Void, SingleResponse<twitter4j.Status>> {

		private final long account_id, status_id;

		public CreateFavoriteTask(final long account_id, final long status_id) {
			super(mContext, mAsyncTaskManager);
			this.account_id = account_id;
			this.status_id = status_id;
		}

		@Override
		protected SingleResponse<twitter4j.Status> doInBackground(final Void... params) {

			if (account_id < 0) return new TwitterSingleResponse<twitter4j.Status>(account_id, null, null);

			final Twitter twitter = getTwitterInstance(mContext, account_id, false);
			if (twitter != null) {
				try {
					final twitter4j.Status status = twitter.createFavorite(status_id);
					return new TwitterSingleResponse<twitter4j.Status>(account_id, status, null);
				} catch (final TwitterException e) {
					return new TwitterSingleResponse<twitter4j.Status>(account_id, null, e);
				}
			}
			return new TwitterSingleResponse<twitter4j.Status>(account_id, null, null);
		}

		@Override
		protected void onPostExecute(final SingleResponse<twitter4j.Status> result) {

			if (result.data != null) {
				final ContentValues values = new ContentValues();
				values.put(Statuses.IS_FAVORITE, true);
				final StringBuilder where = new StringBuilder();
				where.append(Statuses.ACCOUNT_ID + " = " + account_id);
				where.append(" AND ");
				where.append("(");
				where.append(Statuses.STATUS_ID + " = " + status_id);
				where.append(" OR ");
				where.append(Statuses.RETWEET_ID + " = " + status_id);
				where.append(")");
				for (final Uri uri : TweetStore.STATUSES_URIS) {
					mResolver.update(uri, values, where.toString(), null);
				}
				final Intent intent = new Intent(BROADCAST_FAVORITE_CHANGED);
				intent.putExtra(INTENT_KEY_STATUS_ID, status_id);
				intent.putExtra(INTENT_KEY_FAVORITED, true);
				mContext.sendBroadcast(intent);
				Toast.makeText(mContext, R.string.favorite_successful, Toast.LENGTH_SHORT).show();
			} else {
				showErrorToast(R.string.favoriting, result.exception, true);
			}
			super.onPostExecute(result);
		}

	}

	class CreateFriendshipTask extends ManagedAsyncTask<Void, Void, SingleResponse<User>> {

		private final long account_id;
		private final long user_id;

		public CreateFriendshipTask(final long account_id, final long user_id) {
			super(mContext, mAsyncTaskManager);
			this.account_id = account_id;
			this.user_id = user_id;
		}

		@Override
		protected SingleResponse<User> doInBackground(final Void... params) {

			final Twitter twitter = getTwitterInstance(mContext, account_id, false);
			if (twitter != null) {
				try {
					final User user = twitter.createFriendship(user_id);
					return new TwitterSingleResponse<User>(account_id, user, null);
				} catch (final TwitterException e) {
					return new TwitterSingleResponse<User>(account_id, null, e);
				}
			}
			return new TwitterSingleResponse<User>(account_id, null, null);
		}

		@Override
		protected void onPostExecute(final SingleResponse<User> result) {
			if (result != null && result.data != null) {
				Toast.makeText(mContext, R.string.follow_successful, Toast.LENGTH_SHORT).show();
			} else {
				showErrorToast(R.string.following, result.exception, true);
			}
			final Intent intent = new Intent(BROADCAST_FRIENDSHIP_CHANGED);
			intent.putExtra(INTENT_KEY_USER_ID, user_id);
			intent.putExtra(INTENT_KEY_SUCCEED, result != null && result.data != null);
			mContext.sendBroadcast(intent);
			super.onPostExecute(result);
		}

	}

	class CreateMultiBlockTask extends ManagedAsyncTask<Void, Void, ListResponse<Long>> {

		private final long account_id;
		private final long[] user_ids;

		public CreateMultiBlockTask(final long account_id, final long[] user_ids) {
			super(mContext, mAsyncTaskManager);
			this.account_id = account_id;
			this.user_ids = user_ids;
		}

		@Override
		protected ListResponse<Long> doInBackground(final Void... params) {

			final Bundle bundle = new Bundle();
			bundle.putLong(INTENT_KEY_ACCOUNT_ID, account_id);
			final List<Long> blocked_users = new ArrayList<Long>();
			final Twitter twitter = getTwitterInstance(mContext, account_id, false);
			if (twitter != null) {
				for (final long user_id : user_ids) {
					try {
						final User user = twitter.createBlock(user_id);
						if (user == null || user.getId() <= 0) {
							continue;
						}
						blocked_users.add(user.getId());
					} catch (final TwitterException e) {
						return new ListResponse<Long>(null, e, bundle);
					}
				}
			}
			return new ListResponse<Long>(blocked_users, null, bundle);
		}

		@Override
		protected void onPostExecute(final ListResponse<Long> result) {
			if (result.list != null) {
				final String user_ids = ListUtils.toString(result.list, ',', false);
				for (final Uri uri : STATUSES_URIS) {
					final String where = Statuses.ACCOUNT_ID + " = " + account_id + " AND " + Statuses.USER_ID
							+ " IN (" + user_ids + ")";
					mResolver.delete(uri, where, null);
				}
				// I bet you don't want to see these users in your auto complete
				// list.
				final String where = CachedUsers.USER_ID + " IN (" + user_ids + ")";
				mResolver.delete(CachedUsers.CONTENT_URI, where, null);
				Toast.makeText(mContext, R.string.users_blocked, Toast.LENGTH_SHORT).show();
			} else {
				showErrorToast(R.string.blocking, result.exception, true);
			}
			final Intent intent = new Intent(BROADCAST_MULTI_BLOCKSTATE_CHANGED);
			intent.putExtra(INTENT_KEY_USER_ID, user_ids);
			intent.putExtra(INTENT_KEY_SUCCEED, result.list != null);
			mContext.sendBroadcast(intent);
			super.onPostExecute(result);
		}

	}

	class CreateUserListSubscriptionTask extends ManagedAsyncTask<Void, Void, SingleResponse<UserList>> {

		private final long account_id;
		private final int list_id;

		public CreateUserListSubscriptionTask(final long account_id, final int list_id) {
			super(mContext, mAsyncTaskManager);
			this.account_id = account_id;
			this.list_id = list_id;
		}

		@Override
		protected SingleResponse<UserList> doInBackground(final Void... params) {

			final Twitter twitter = getTwitterInstance(mContext, account_id, false);
			if (twitter != null) {
				try {
					final UserList list = twitter.createUserListSubscription(list_id);
					return new TwitterSingleResponse<UserList>(account_id, list, null);
				} catch (final TwitterException e) {
					return new TwitterSingleResponse<UserList>(account_id, null, e);
				}
			}
			return new TwitterSingleResponse<UserList>(account_id, null, null);
		}

		@Override
		protected void onPostExecute(final SingleResponse<UserList> result) {
			final boolean succeed = result != null && result.data != null && result.data.getId() > 0;
			if (succeed) {
				Toast.makeText(mContext, R.string.follow_successful, Toast.LENGTH_SHORT).show();
			} else {
				showErrorToast(R.string.following, result.exception, true);
			}
			final Intent intent = new Intent(BROADCAST_USER_LIST_SUBSCRIPTION_CHANGED);
			intent.putExtra(INTENT_KEY_LIST_ID, list_id);
			intent.putExtra(INTENT_KEY_SUCCEED, succeed);
			mContext.sendBroadcast(intent);
			super.onPostExecute(result);
		}

	}

	class CreateUserListTask extends ManagedAsyncTask<Void, Void, SingleResponse<UserList>> {

		private final long account_id;
		private final String list_name, description;
		private final boolean is_public;

		public CreateUserListTask(final long account_id, final String list_name, final boolean is_public,
				final String description) {
			super(mContext, mAsyncTaskManager);
			this.account_id = account_id;
			this.list_name = list_name;
			this.description = description;
			this.is_public = is_public;
		}

		@Override
		protected SingleResponse<UserList> doInBackground(final Void... params) {

			final Twitter twitter = getTwitterInstance(mContext, account_id, false);
			if (twitter != null) {
				try {
					if (list_name != null) {
						final UserList list = twitter.createUserList(list_name, is_public, description);
						return new TwitterSingleResponse<UserList>(account_id, list, null);
					}
				} catch (final TwitterException e) {
					return new TwitterSingleResponse<UserList>(account_id, null, e);
				}
			}
			return new TwitterSingleResponse<UserList>(account_id, null, null);
		}

		@Override
		protected void onPostExecute(final SingleResponse<UserList> result) {
			final boolean succeed = result != null && result.data != null && result.data.getId() > 0;
			if (succeed) {
				Toast.makeText(mContext, R.string.create_successful, Toast.LENGTH_SHORT).show();
			} else {
				showErrorToast(R.string.creating_list, result.exception, true);
			}
			final Intent intent = new Intent(BROADCAST_USER_LIST_CREATED);
			intent.putExtra(INTENT_KEY_SUCCEED, succeed);
			mContext.sendBroadcast(intent);
			super.onPostExecute(result);
		}

	}

	class DeleteUserListMemberTask extends ManagedAsyncTask<Void, Void, SingleResponse<UserList>> {

		private final long account_id, user_id;
		private final int list_id;

		public DeleteUserListMemberTask(final long account_id, final int list_id, final long user_id) {
			super(mContext, mAsyncTaskManager);
			this.account_id = account_id;
			this.list_id = list_id;
			this.user_id = user_id;
		}

		@Override
		protected SingleResponse<UserList> doInBackground(final Void... params) {

			final Twitter twitter = getTwitterInstance(mContext, account_id, false);
			if (twitter != null) {
				try {
					final UserList list = twitter.deleteUserListMember(list_id, user_id);
					return new TwitterSingleResponse<UserList>(account_id, list, null);
				} catch (final TwitterException e) {
					return new TwitterSingleResponse<UserList>(account_id, null, e);
				}
			}
			return new TwitterSingleResponse<UserList>(account_id, null, null);
		}

		@Override
		protected void onPostExecute(final SingleResponse<UserList> result) {
			final boolean succeed = result != null && result.data != null && result.data.getId() > 0;
			if (succeed) {
				Toast.makeText(mContext, R.string.delete_successful, Toast.LENGTH_SHORT).show();
			} else {
				showErrorToast(R.string.deleting, result.exception, true);
			}
			final Intent intent = new Intent(BROADCAST_USER_LIST_MEMBER_DELETED);
			intent.putExtra(INTENT_KEY_USER_ID, user_id);
			intent.putExtra(INTENT_KEY_LIST_ID, list_id);
			intent.putExtra(INTENT_KEY_SUCCEED, succeed);
			mContext.sendBroadcast(intent);
			super.onPostExecute(result);
		}

	}

	class DestroyBlockTask extends ManagedAsyncTask<Void, Void, SingleResponse<User>> {

		private final long account_id;
		private final long user_id;

		public DestroyBlockTask(final long account_id, final long user_id) {
			super(mContext, mAsyncTaskManager);
			this.account_id = account_id;
			this.user_id = user_id;
		}

		@Override
		protected SingleResponse<User> doInBackground(final Void... params) {

			final Twitter twitter = getTwitterInstance(mContext, account_id, false);
			if (twitter != null) {
				try {
					final User user = twitter.destroyBlock(user_id);
					return new TwitterSingleResponse<User>(account_id, user, null);
				} catch (final TwitterException e) {
					return new TwitterSingleResponse<User>(account_id, null, e);
				}
			}
			return new TwitterSingleResponse<User>(account_id, null, null);
		}

		@Override
		protected void onPostExecute(final SingleResponse<User> result) {
			if (result != null && result.data != null) {
				Toast.makeText(mContext, R.string.user_unblocked, Toast.LENGTH_SHORT).show();
			} else {
				showErrorToast(R.string.unblocking, result.exception, true);
			}
			final Intent intent = new Intent(BROADCAST_BLOCKSTATE_CHANGED);
			intent.putExtra(INTENT_KEY_USER_ID, user_id);
			intent.putExtra(INTENT_KEY_SUCCEED, result != null && result.data != null);
			mContext.sendBroadcast(intent);
			super.onPostExecute(result);
		}

	}

	class DestroyDirectMessageTask extends ManagedAsyncTask<Void, Void, SingleResponse<DirectMessage>> {

		private final Twitter twitter;
		private final long message_id;
		private final long account_id;

		public DestroyDirectMessageTask(final long account_id, final long message_id) {
			super(mContext, mAsyncTaskManager);
			twitter = getTwitterInstance(mContext, account_id, false);
			this.account_id = account_id;
			this.message_id = message_id;
		}

		@Override
		protected SingleResponse<DirectMessage> doInBackground(final Void... args) {
			if (twitter == null) return new TwitterSingleResponse<DirectMessage>(account_id, null, null);
			try {
				return new TwitterSingleResponse<DirectMessage>(account_id, twitter.destroyDirectMessage(message_id),
						null);
			} catch (final TwitterException e) {
				return new TwitterSingleResponse<DirectMessage>(account_id, null, e);
			}
		}

		@Override
		protected void onPostExecute(final SingleResponse<DirectMessage> result) {
			super.onPostExecute(result);
			if (result == null) return;
			if (result.data != null && result.data.getId() > 0 || result.exception instanceof TwitterException
					&& ((TwitterException) result.exception).getErrorCode() == 34) {
				Toast.makeText(mContext, R.string.delete_successful, Toast.LENGTH_SHORT).show();
				final String where = DirectMessages.MESSAGE_ID + " = " + message_id;
				mResolver.delete(DirectMessages.Inbox.CONTENT_URI, where, null);
				mResolver.delete(DirectMessages.Outbox.CONTENT_URI, where, null);
			} else {
				showErrorToast(R.string.deleting, result.exception, true);
			}
		}

	}

	class DestroyFavoriteTask extends ManagedAsyncTask<Void, Void, SingleResponse<twitter4j.Status>> {

		private final long account_id;

		private final long status_id;

		public DestroyFavoriteTask(final long account_id, final long status_id) {
			super(mContext, mAsyncTaskManager);
			this.account_id = account_id;
			this.status_id = status_id;
		}

		@Override
		protected SingleResponse<twitter4j.Status> doInBackground(final Void... params) {

			if (account_id < 0) {
				new TwitterSingleResponse<twitter4j.Status>(account_id, null, null);
			}

			final Twitter twitter = getTwitterInstance(mContext, account_id, false);
			if (twitter != null) {
				try {
					final twitter4j.Status status = twitter.destroyFavorite(status_id);
					return new TwitterSingleResponse<twitter4j.Status>(account_id, status, null);
				} catch (final TwitterException e) {
					return new TwitterSingleResponse<twitter4j.Status>(account_id, null, e);
				}
			}
			return new TwitterSingleResponse<twitter4j.Status>(account_id, null, null);
		}

		@Override
		protected void onPostExecute(final SingleResponse<twitter4j.Status> result) {

			if (result.data != null) {
				final long status_id = result.data.getId();
				final ContentValues values = new ContentValues();
				values.put(Statuses.IS_FAVORITE, 0);
				final StringBuilder where = new StringBuilder();
				where.append(Statuses.ACCOUNT_ID + " = " + account_id);
				where.append(" AND ");
				where.append("(");
				where.append(Statuses.STATUS_ID + " = " + status_id);
				where.append(" OR ");
				where.append(Statuses.RETWEET_ID + " = " + status_id);
				where.append(")");
				for (final Uri uri : TweetStore.STATUSES_URIS) {
					mResolver.update(uri, values, where.toString(), null);
				}
				final Intent intent = new Intent(BROADCAST_FAVORITE_CHANGED);
				intent.putExtra(INTENT_KEY_USER_ID, account_id);
				intent.putExtra(INTENT_KEY_STATUS_ID, status_id);
				intent.putExtra(INTENT_KEY_FAVORITED, false);
				mContext.sendBroadcast(intent);
				Toast.makeText(mContext, R.string.unfavorite_successful, Toast.LENGTH_SHORT).show();

			} else {
				showErrorToast(R.string.unfavoriting, result.exception, true);
			}
			super.onPostExecute(result);
		}

	}

	class DestroyFriendshipTask extends ManagedAsyncTask<Void, Void, SingleResponse<User>> {

		private final long account_id;
		private final long user_id;

		public DestroyFriendshipTask(final long account_id, final long user_id) {
			super(mContext, mAsyncTaskManager);
			this.account_id = account_id;
			this.user_id = user_id;
		}

		@Override
		protected SingleResponse<User> doInBackground(final Void... params) {

			final Twitter twitter = getTwitterInstance(mContext, account_id, false);
			if (twitter != null) {
				try {
					final User user = twitter.destroyFriendship(user_id);
					return new TwitterSingleResponse<User>(account_id, user, null);
				} catch (final TwitterException e) {
					return new TwitterSingleResponse<User>(account_id, null, e);
				}
			}
			return new TwitterSingleResponse<User>(account_id, null, null);
		}

		@Override
		protected void onPostExecute(final SingleResponse<User> result) {
			if (result != null && result.data != null) {
				Toast.makeText(mContext, R.string.unfollow_successful, Toast.LENGTH_SHORT).show();
			} else {
				showErrorToast(R.string.unfollowing, result.exception, true);
			}
			final Intent intent = new Intent(BROADCAST_FRIENDSHIP_CHANGED);
			intent.putExtra(INTENT_KEY_USER_ID, user_id);
			intent.putExtra(INTENT_KEY_SUCCEED, result != null && result.data != null);
			mContext.sendBroadcast(intent);
			super.onPostExecute(result);
		}

	}

	class DestroyStatusTask extends ManagedAsyncTask<Void, Void, SingleResponse<twitter4j.Status>> {

		private final long account_id;

		private final long status_id;

		public DestroyStatusTask(final long account_id, final long status_id) {
			super(mContext, mAsyncTaskManager);
			this.account_id = account_id;
			this.status_id = status_id;
		}

		@Override
		protected SingleResponse<twitter4j.Status> doInBackground(final Void... params) {

			final Twitter twitter = getTwitterInstance(mContext, account_id, false);
			if (twitter != null) {
				try {
					final twitter4j.Status status = twitter.destroyStatus(status_id);
					return new TwitterSingleResponse<twitter4j.Status>(account_id, status, null);
				} catch (final TwitterException e) {
					return new TwitterSingleResponse<twitter4j.Status>(account_id, null, e);
				}
			}
			return new TwitterSingleResponse<twitter4j.Status>(account_id, null, null);
		}

		@Override
		protected void onPostExecute(final SingleResponse<twitter4j.Status> result) {
			final Intent intent = new Intent(BROADCAST_STATUS_DESTROYED);
			if (result != null && result.data != null && result.data.getId() > 0) {
				final long status_id = result.data.getId();
				final ContentValues values = new ContentValues();
				values.put(Statuses.MY_RETWEET_ID, -1);
				for (final Uri uri : TweetStore.STATUSES_URIS) {
					mResolver.delete(uri, Statuses.STATUS_ID + " = " + status_id, null);
					mResolver.update(uri, values, Statuses.MY_RETWEET_ID + " = " + status_id, null);
				}
				intent.putExtra(INTENT_KEY_STATUS_ID, status_id);
				intent.putExtra(INTENT_KEY_SUCCEED, true);
				Toast.makeText(mContext, R.string.delete_successful, Toast.LENGTH_SHORT).show();
			} else {
				showErrorToast(R.string.deleting, result.exception, true);
			}
			mContext.sendBroadcast(intent);
			super.onPostExecute(result);
		}

	}

	class DestroyUserListSubscriptionTask extends ManagedAsyncTask<Void, Void, SingleResponse<UserList>> {

		private final long account_id;
		private final int list_id;

		public DestroyUserListSubscriptionTask(final long account_id, final int list_id) {
			super(mContext, mAsyncTaskManager);
			this.account_id = account_id;
			this.list_id = list_id;
		}

		@Override
		protected SingleResponse<UserList> doInBackground(final Void... params) {

			final Twitter twitter = getTwitterInstance(mContext, account_id, false);
			if (twitter != null) {
				try {
					final UserList list = twitter.destroyUserListSubscription(list_id);
					return new TwitterSingleResponse<UserList>(account_id, list, null);
				} catch (final TwitterException e) {
					return new TwitterSingleResponse<UserList>(account_id, null, e);
				}
			}
			return new TwitterSingleResponse<UserList>(account_id, null, null);
		}

		@Override
		protected void onPostExecute(final SingleResponse<UserList> result) {
			final boolean succeed = result != null && result.data != null && result.data.getId() > 0;
			if (succeed) {
				Toast.makeText(mContext, R.string.unfollow_successful, Toast.LENGTH_SHORT).show();
			} else {
				showErrorToast(R.string.unfollowing, result.exception, true);
			}
			final Intent intent = new Intent(BROADCAST_USER_LIST_SUBSCRIPTION_CHANGED);
			intent.putExtra(INTENT_KEY_LIST_ID, list_id);
			intent.putExtra(INTENT_KEY_SUCCEED, succeed);
			mContext.sendBroadcast(intent);
			super.onPostExecute(result);
		}

	}

	class DestroyUserListTask extends ManagedAsyncTask<Void, Void, SingleResponse<UserList>> {

		private final long account_id;
		private final int list_id;

		public DestroyUserListTask(final long account_id, final int list_id) {
			super(mContext, mAsyncTaskManager);
			this.account_id = account_id;
			this.list_id = list_id;
		}

		@Override
		protected SingleResponse<UserList> doInBackground(final Void... params) {

			final Twitter twitter = getTwitterInstance(mContext, account_id, false);
			if (twitter != null) {
				try {
					if (list_id > 0) {
						final UserList list = twitter.destroyUserList(list_id);
						return new TwitterSingleResponse<UserList>(account_id, list, null);
					}
				} catch (final TwitterException e) {
					return new TwitterSingleResponse<UserList>(account_id, null, e);
				}
			}
			return new TwitterSingleResponse<UserList>(account_id, null, null);
		}

		@Override
		protected void onPostExecute(final SingleResponse<UserList> result) {
			final boolean succeed = result != null && result.data != null && result.data.getId() > 0;
			if (succeed) {
				Toast.makeText(mContext, R.string.delete_successful, Toast.LENGTH_SHORT).show();
			} else {
				showErrorToast(R.string.deleting, result.exception, true);
			}
			final Intent intent = new Intent(BROADCAST_USER_LIST_DELETED);
			intent.putExtra(INTENT_KEY_SUCCEED, succeed);
			intent.putExtra(INTENT_KEY_LIST_ID, list_id);
			mContext.sendBroadcast(intent);
			super.onPostExecute(result);
		}

	}

	abstract class GetDirectMessagesTask extends ManagedAsyncTask<Void, Void, List<TwitterListResponse<DirectMessage>>> {

		private final long[] account_ids, max_ids, since_ids;

		public GetDirectMessagesTask(final long[] account_ids, final long[] max_ids, final long[] since_ids,
				final String tag) {
			super(mContext, mAsyncTaskManager, tag);
			this.account_ids = account_ids;
			this.max_ids = max_ids;
			this.since_ids = since_ids;
		}

		public abstract ResponseList<DirectMessage> getDirectMessages(Twitter twitter, Paging paging)
				throws TwitterException;

		@Override
		protected List<TwitterListResponse<DirectMessage>> doInBackground(final Void... params) {

			final List<TwitterListResponse<DirectMessage>> result = new ArrayList<TwitterListResponse<DirectMessage>>();

			if (account_ids == null) return result;

			int idx = 0;
			final int load_item_limit = mPreferences.getInt(PREFERENCE_KEY_LOAD_ITEM_LIMIT,
					PREFERENCE_DEFAULT_LOAD_ITEM_LIMIT);
			for (final long account_id : account_ids) {
				final Twitter twitter = getTwitterInstance(mContext, account_id, true);
				if (twitter != null) {
					try {
						final Paging paging = new Paging();
						paging.setCount(load_item_limit);
						long max_id = -1, since_id = -1;
						if (isMaxIdsValid() && max_ids[idx] > 0) {
							max_id = max_ids[idx];
							paging.setMaxId(max_id);
						}
						if (isSinceIdsValid() && since_ids[idx] > 0) {
							since_id = since_ids[idx];
							paging.setSinceId(since_id);
						}
						final ResponseList<DirectMessage> statuses = getDirectMessages(twitter, paging);

						if (statuses != null) {
							result.add(new TwitterListResponse<DirectMessage>(account_id, max_id, since_id,
									load_item_limit, statuses, null));
						}
					} catch (final TwitterException e) {
						result.add(new TwitterListResponse<DirectMessage>(account_id, -1, -1, load_item_limit, null, e));
					}
				}
				idx++;
			}
			return result;

		}

		@Override
		protected void onPostExecute(final List<TwitterListResponse<DirectMessage>> result) {
			super.onPostExecute(result);
			for (final TwitterListResponse<DirectMessage> response : result) {
				if (response.list == null) {
					showErrorToast(R.string.refreshing_direct_messages, response.exception, true);
				}
			}
		}

		final boolean isMaxIdsValid() {
			return max_ids != null && max_ids.length == account_ids.length;
		}

		final boolean isSinceIdsValid() {
			return since_ids != null && since_ids.length == account_ids.length;
		}

	}

	class GetHomeTimelineTask extends GetStatusesTask {

		public GetHomeTimelineTask(final long[] account_ids, final long[] max_ids, final long[] since_ids) {
			super(account_ids, max_ids, since_ids, TASK_TAG_GET_HOME_TIMELINE);
		}

		@Override
		public ResponseList<twitter4j.Status> getStatuses(final Twitter twitter, final Paging paging)
				throws TwitterException {
			return twitter.getHomeTimeline(paging);
		}

		@Override
		public Twitter getTwitter(final long account_id) {
			return getTwitterInstance(mContext, account_id, true);
		}

		@Override
		protected void onPostExecute(final List<StatusListResponse> responses) {
			super.onPostExecute(responses);
			mAsyncTaskManager.add(new StoreHomeTimelineTask(responses, shouldSetMinId(), !isMaxIdsValid()), true);
			mGetHomeTimelineTaskId = -1;
		}

		@Override
		protected void onPreExecute() {
			final Intent intent = new Intent(BROADCAST_RESCHEDULE_HOME_TIMELINE_REFRESHING);
			mContext.sendBroadcast(intent);
			super.onPreExecute();
		}

	}

	class GetLocalTrendsTask extends GetTrendsTask {

		private final int woeid;

		public GetLocalTrendsTask(final long account_id, final int woeid) {
			super(account_id);
			this.woeid = woeid;
		}

		@Override
		public List<Trends> getTrends(final Twitter twitter) throws TwitterException {
			final ArrayList<Trends> trends_list = new ArrayList<Trends>();
			if (twitter != null) {
				trends_list.add(twitter.getLocationTrends(woeid));
			}
			return trends_list;
		}

		@Override
		protected void onPostExecute(final ListResponse<Trends> result) {
			mAsyncTaskManager.add(new StoreLocalTrendsTask(result), true);
			super.onPostExecute(result);

		}

	}

	class GetMentionsTask extends GetStatusesTask {

		public GetMentionsTask(final long[] account_ids, final long[] max_ids, final long[] since_ids) {
			super(account_ids, max_ids, since_ids, TASK_TAG_GET_MENTIONS);
		}

		@Override
		public ResponseList<twitter4j.Status> getStatuses(final Twitter twitter, final Paging paging)
				throws TwitterException {
			return twitter.getMentionsTimeline(paging);
		}

		@Override
		public Twitter getTwitter(final long account_id) {
			return getTwitterInstance(mContext, account_id, true);
		}

		@Override
		protected void onPostExecute(final List<StatusListResponse> responses) {
			super.onPostExecute(responses);
			mAsyncTaskManager.add(new StoreMentionsTask(responses, shouldSetMinId(), !isMaxIdsValid()), true);
			mGetMentionsTaskId = -1;
		}

		@Override
		protected void onPreExecute() {

			final Intent intent = new Intent(BROADCAST_RESCHEDULE_MENTIONS_REFRESHING);
			mContext.sendBroadcast(intent);
			super.onPreExecute();
		}

	}

	class GetReceivedDirectMessagesTask extends GetDirectMessagesTask {

		public GetReceivedDirectMessagesTask(final long[] account_ids, final long[] max_ids, final long[] since_ids) {
			super(account_ids, max_ids, since_ids, TASK_TAG_GET_RECEIVED_DIRECT_MESSAGES);
		}

		@Override
		public ResponseList<DirectMessage> getDirectMessages(final Twitter twitter, final Paging paging)
				throws TwitterException {
			return twitter.getDirectMessages(paging);
		}

		@Override
		protected void onPostExecute(final List<TwitterListResponse<DirectMessage>> responses) {
			super.onPostExecute(responses);
			mAsyncTaskManager.add(new StoreReceivedDirectMessagesTask(responses, !isMaxIdsValid()), true);
			mGetReceivedDirectMessagesTaskId = -1;
		}

		@Override
		protected void onPreExecute() {
			final Intent intent = new Intent(BROADCAST_RESCHEDULE_DIRECT_MESSAGES_REFRESHING);
			mContext.sendBroadcast(intent);
			super.onPreExecute();
		}

	}

	class GetSentDirectMessagesTask extends GetDirectMessagesTask {

		public GetSentDirectMessagesTask(final long[] account_ids, final long[] max_ids, final long[] since_ids) {
			super(account_ids, max_ids, since_ids, TASK_TAG_GET_SENT_DIRECT_MESSAGES);
		}

		@Override
		public ResponseList<DirectMessage> getDirectMessages(final Twitter twitter, final Paging paging)
				throws TwitterException {
			return twitter.getSentDirectMessages(paging);
		}

		@Override
		protected void onPostExecute(final List<TwitterListResponse<DirectMessage>> responses) {
			super.onPostExecute(responses);
			mAsyncTaskManager.add(new StoreSentDirectMessagesTask(responses, !isMaxIdsValid()), true);
			mGetSentDirectMessagesTaskId = -1;
		}

	}

	abstract class GetStatusesTask extends ManagedAsyncTask<Void, Void, List<StatusListResponse>> {

		private final long[] account_ids, max_ids, since_ids;

		public GetStatusesTask(final long[] account_ids, final long[] max_ids, final long[] since_ids, final String tag) {
			super(mContext, mAsyncTaskManager, tag);
			this.account_ids = account_ids;
			this.max_ids = max_ids;
			this.since_ids = since_ids;
		}

		public abstract ResponseList<twitter4j.Status> getStatuses(Twitter twitter, Paging paging)
				throws TwitterException;

		public abstract Twitter getTwitter(long account_id);

		@Override
		protected List<StatusListResponse> doInBackground(final Void... params) {

			final List<StatusListResponse> result = new ArrayList<StatusListResponse>();

			if (account_ids == null) return result;

			int idx = 0;
			final int load_item_limit = mPreferences.getInt(PREFERENCE_KEY_LOAD_ITEM_LIMIT,
					PREFERENCE_DEFAULT_LOAD_ITEM_LIMIT);
			for (final long account_id : account_ids) {
				final Twitter twitter = getTwitter(account_id);
				if (twitter != null) {
					try {
						final Paging paging = new Paging();
						paging.setCount(load_item_limit);
						long max_id = -1, since_id = -1;
						if (isMaxIdsValid() && max_ids[idx] > 0) {
							max_id = max_ids[idx];
							paging.setMaxId(max_id);
						}
						if (isSinceIdsValid() && since_ids[idx] > 0) {
							since_id = since_ids[idx];
							paging.setSinceId(since_id);
						}
						final ResponseList<twitter4j.Status> statuses = getStatuses(twitter, paging);
						if (statuses != null) {
							result.add(new StatusListResponse(account_id, max_id, since_id, load_item_limit, statuses,
									null));
						}
					} catch (final TwitterException e) {
						result.add(new StatusListResponse(account_id, -1, -1, load_item_limit, null, e));
					}
				}
				idx++;
			}
			return result;
		}

		@Override
		protected void onPostExecute(final List<StatusListResponse> result) {
			super.onPostExecute(result);
			for (final StatusListResponse response : result) {
				if (response.list == null) {
					showErrorToast(R.string.refreshing_timelines, response.exception, true);
				}
			}
		}

		final boolean isMaxIdsValid() {
			return max_ids != null && max_ids.length == account_ids.length;
		}

		final boolean isSinceIdsValid() {
			return since_ids != null && since_ids.length == account_ids.length;
		}

		final boolean shouldSetMinId() {
			return !isMaxIdsValid();
		}

	}

	abstract class GetTrendsTask extends ManagedAsyncTask<Void, Void, ListResponse<Trends>> {

		private final long account_id;

		public GetTrendsTask(final long account_id) {
			super(mContext, mAsyncTaskManager, TASK_TAG_GET_TRENDS);
			this.account_id = account_id;
		}

		public abstract List<Trends> getTrends(Twitter twitter) throws TwitterException;

		@Override
		protected ListResponse<Trends> doInBackground(final Void... params) {
			final Twitter twitter = getTwitterInstance(mContext, account_id, false);
			final Bundle extras = new Bundle();
			extras.putLong(INTENT_KEY_ACCOUNT_ID, account_id);
			if (twitter != null) {
				try {
					return new ListResponse<Trends>(getTrends(twitter), null, extras);
				} catch (final TwitterException e) {
					return new ListResponse<Trends>(null, e, extras);
				}
			}
			return new ListResponse<Trends>(null, null, extras);
		}

	}

	class ReportMultiSpamTask extends ManagedAsyncTask<Void, Void, ListResponse<Long>> {

		private final long account_id;
		private final long[] user_ids;

		public ReportMultiSpamTask(final long account_id, final long[] user_ids) {
			super(mContext, mAsyncTaskManager);
			this.account_id = account_id;
			this.user_ids = user_ids;
		}

		@Override
		protected ListResponse<Long> doInBackground(final Void... params) {

			final Bundle extras = new Bundle();
			extras.putLong(INTENT_KEY_ACCOUNT_ID, account_id);
			final List<Long> reported_users = new ArrayList<Long>();
			final Twitter twitter = getTwitterInstance(mContext, account_id, false);
			if (twitter != null) {
				for (final long user_id : user_ids) {
					try {
						final User user = twitter.reportSpam(user_id);
						if (user == null || user.getId() <= 0) {
							continue;
						}
						reported_users.add(user.getId());
					} catch (final TwitterException e) {
						return new ListResponse<Long>(null, e, extras);
					}
				}
			}
			return new ListResponse<Long>(reported_users, null, extras);
		}

		@Override
		protected void onPostExecute(final ListResponse<Long> result) {
			if (result != null) {
				final String user_id_where = ListUtils.toString(result.list, ',', false);
				for (final Uri uri : STATUSES_URIS) {
					final String where = Statuses.ACCOUNT_ID + " = " + account_id + " AND " + Statuses.USER_ID
							+ " IN (" + user_id_where + ")";
					mResolver.delete(uri, where, null);
				}
				Toast.makeText(mContext, R.string.reported_users_for_spam, Toast.LENGTH_SHORT).show();
			}
			final Intent intent = new Intent(BROADCAST_MULTI_BLOCKSTATE_CHANGED);
			intent.putExtra(INTENT_KEY_USER_IDS, user_ids);
			intent.putExtra(INTENT_KEY_ACCOUNT_ID, account_id);
			intent.putExtra(INTENT_KEY_SUCCEED, result != null);
			mContext.sendBroadcast(intent);
			super.onPostExecute(result);
		}

	}

	class ReportSpamTask extends ManagedAsyncTask<Void, Void, SingleResponse<User>> {

		private final long account_id;
		private final long user_id;

		public ReportSpamTask(final long account_id, final long user_id) {
			super(mContext, mAsyncTaskManager);
			this.account_id = account_id;
			this.user_id = user_id;
		}

		@Override
		protected SingleResponse<User> doInBackground(final Void... params) {

			final Twitter twitter = getTwitterInstance(mContext, account_id, false);
			if (twitter != null) {
				try {
					final User user = twitter.reportSpam(user_id);
					return new TwitterSingleResponse<User>(account_id, user, null);
				} catch (final TwitterException e) {
					return new TwitterSingleResponse<User>(account_id, null, e);
				}
			}
			return new TwitterSingleResponse<User>(account_id, null, null);
		}

		@Override
		protected void onPostExecute(final SingleResponse<User> result) {
			if (result != null && result.data != null && result.data.getId() > 0) {
				for (final Uri uri : STATUSES_URIS) {
					final String where = Statuses.ACCOUNT_ID + " = " + account_id + " AND " + Statuses.USER_ID + " = "
							+ user_id;
					mResolver.delete(uri, where, null);
				}
				Toast.makeText(mContext, R.string.reported_user_for_spam, Toast.LENGTH_SHORT).show();
			} else {
				showErrorToast(R.string.reporting_for_spam, result.exception, true);
			}
			final Intent intent = new Intent(BROADCAST_BLOCKSTATE_CHANGED);
			intent.putExtra(INTENT_KEY_USER_ID, user_id);
			intent.putExtra(INTENT_KEY_SUCCEED, result != null && result.data != null);
			mContext.sendBroadcast(intent);
			super.onPostExecute(result);
		}

	}

	class RetweetStatusTask extends ManagedAsyncTask<Void, Void, SingleResponse<twitter4j.Status>> {

		private final long account_id;

		private final long status_id;

		public RetweetStatusTask(final long account_id, final long status_id) {
			super(mContext, mAsyncTaskManager);
			this.account_id = account_id;
			this.status_id = status_id;
		}

		@Override
		protected SingleResponse<twitter4j.Status> doInBackground(final Void... params) {

			if (account_id < 0) return new TwitterSingleResponse<twitter4j.Status>(account_id, null, null);

			final Twitter twitter = getTwitterInstance(mContext, account_id, false);
			if (twitter != null) {
				try {
					final twitter4j.Status status = twitter.retweetStatus(status_id);
					return new TwitterSingleResponse<twitter4j.Status>(account_id, status, null);
				} catch (final TwitterException e) {
					return new TwitterSingleResponse<twitter4j.Status>(account_id, null, e);
				}
			}
			return new TwitterSingleResponse<twitter4j.Status>(account_id, null, null);
		}

		@Override
		protected void onPostExecute(final SingleResponse<twitter4j.Status> result) {

			if (result.data != null && result.data.getId() > 0) {
				final ContentValues values = new ContentValues();
				values.put(Statuses.MY_RETWEET_ID, result.data.getId());
				final String where = Statuses.STATUS_ID + " = " + status_id + " OR " + Statuses.RETWEET_ID + " = "
						+ status_id;
				for (final Uri uri : STATUSES_URIS) {
					mResolver.update(uri, values, where, null);
				}
				final Intent intent = new Intent(BROADCAST_RETWEET_CHANGED);
				intent.putExtra(INTENT_KEY_STATUS_ID, status_id);
				intent.putExtra(INTENT_KEY_RETWEETED, true);
				mContext.sendBroadcast(intent);
				Toast.makeText(mContext, R.string.retweet_successful, Toast.LENGTH_SHORT).show();
			} else {
				showErrorToast(R.string.retweeting, result.exception, true);
			}

			super.onPostExecute(result);
		}

	}

	class SendDirectMessageTask extends ManagedAsyncTask<Void, Void, SingleResponse<DirectMessage>> {

		private final Twitter twitter;
		private final long user_id;
		private final String screen_name;
		private final String message;
		private final long account_id;

		public SendDirectMessageTask(final long account_id, final String screen_name, final long user_id,
				final String message) {
			super(mContext, mAsyncTaskManager);
			twitter = getTwitterInstance(mContext, account_id, true, true);
			this.account_id = account_id;
			this.user_id = user_id;
			this.screen_name = screen_name;
			this.message = message;
		}

		@Override
		protected SingleResponse<DirectMessage> doInBackground(final Void... args) {
			if (twitter == null) return new TwitterSingleResponse<DirectMessage>(account_id, null, null);
			try {
				if (user_id > 0)
					return new TwitterSingleResponse<DirectMessage>(account_id, twitter.sendDirectMessage(user_id,
							message), null);
				else if (screen_name != null)
					return new TwitterSingleResponse<DirectMessage>(account_id, twitter.sendDirectMessage(screen_name,
							message), null);
			} catch (final TwitterException e) {
				return new TwitterSingleResponse<DirectMessage>(account_id, null, e);
			}
			return new TwitterSingleResponse<DirectMessage>(account_id, null, null);
		}

		@Override
		protected void onPostExecute(final SingleResponse<DirectMessage> result) {
			super.onPostExecute(result);
			if (result == null) return;
			if (result.data != null && result.data.getId() > 0) {
				final ContentValues values = makeDirectMessageContentValues(result.data, account_id, true,
						large_profile_image);
				mResolver.insert(DirectMessages.Outbox.CONTENT_URI, values);
				Toast.makeText(mContext, R.string.send_successful, Toast.LENGTH_SHORT).show();
			} else {
				showErrorToast(R.string.sending_direct_message, result.exception, true);
			}
		}

	}

	abstract class StoreDirectMessagesTask extends ManagedAsyncTask<Void, Void, SingleResponse<Bundle>> {

		private final List<TwitterListResponse<DirectMessage>> responses;
		private final Uri uri;

		public StoreDirectMessagesTask(final List<TwitterListResponse<DirectMessage>> result, final Uri uri,
				final boolean notify, final String tag) {
			super(mContext, mAsyncTaskManager, tag);
			responses = result;
			this.uri = uri.buildUpon().appendQueryParameter(QUERY_PARAM_NOTIFY, String.valueOf(notify)).build();
		}

		@Override
		protected SingleResponse<Bundle> doInBackground(final Void... args) {

			boolean succeed = false;
			for (final TwitterListResponse<DirectMessage> response : responses) {
				final long account_id = response.account_id;
				final List<DirectMessage> messages = response.list;
				if (messages != null) {
					final List<ContentValues> values_list = new ArrayList<ContentValues>();
					final List<Long> message_ids = new ArrayList<Long>();

					for (final DirectMessage message : messages) {
						if (message == null || message.getId() <= 0) {
							continue;
						}
						message_ids.add(message.getId());
						values_list.add(makeDirectMessageContentValues(message, account_id, isOutgoing(),
								large_profile_image));
					}

					// Delete all rows conflicting before new data inserted.
					{
						final StringBuilder where = new StringBuilder();
						where.append(DirectMessages.ACCOUNT_ID + " = " + account_id);
						where.append(" AND ");
						where.append(DirectMessages.MESSAGE_ID + " IN ( " + ListUtils.toString(message_ids, ',', true)
								+ " ) ");
						final Uri delete_uri = appendQueryParameters(uri, new NameValuePairImpl(QUERY_PARAM_NOTIFY,
								false));
						mResolver.delete(delete_uri, where.toString(), null);
					}

					// Insert previously fetched items.
					final Uri insert_uri = appendQueryParameters(uri, new NameValuePairImpl(QUERY_PARAM_NOTIFY, false));
					mResolver.bulkInsert(insert_uri, values_list.toArray(new ContentValues[values_list.size()]));

				}
				succeed = true;
			}
			final Bundle bundle = new Bundle();
			bundle.putBoolean(INTENT_KEY_SUCCEED, succeed);
			return new TwitterSingleResponse<Bundle>(-1, bundle, null);
		}

		abstract boolean isOutgoing();

	}

	class StoreHomeTimelineTask extends StoreStatusesTask {

		public StoreHomeTimelineTask(final List<StatusListResponse> result, final boolean should_set_min_id,
				final boolean notify) {
			super(result, Statuses.CONTENT_URI, should_set_min_id, notify, TASK_TAG_STORE_HOME_TIMELINE);
		}

		@Override
		protected void onPostExecute(final SingleResponse<Bundle> response) {
			final boolean succeed = response != null && response.data != null
					&& response.data.getBoolean(INTENT_KEY_SUCCEED);
			final Bundle extras = new Bundle();
			extras.putBoolean(INTENT_KEY_SUCCEED, succeed);
			if (shouldSetMinId()) {
				final long min_id = response != null && response.data != null ? response.data.getLong(
						INTENT_KEY_MIN_ID, -1) : -1;
				extras.putLong(INTENT_KEY_MIN_ID, min_id);
				mPreferences.edit().putLong(PREFERENCE_KEY_SAVED_HOME_TIMELINE_ID, min_id).commit();
			}
			mContext.sendBroadcast(new Intent(BROADCAST_HOME_TIMELINE_REFRESHED).putExtras(extras));
			super.onPostExecute(response);
		}

	}

	class StoreLocalTrendsTask extends StoreTrendsTask {

		public StoreLocalTrendsTask(final ListResponse<Trends> result) {
			super(result, CachedTrends.Local.CONTENT_URI);
		}

	}

	class StoreMentionsTask extends StoreStatusesTask {

		public StoreMentionsTask(final List<StatusListResponse> result, final boolean should_set_min_id,
				final boolean notify) {
			super(result, Mentions.CONTENT_URI, should_set_min_id, notify, TASK_TAG_STORE_MENTIONS);
		}

		@Override
		protected void onPostExecute(final SingleResponse<Bundle> response) {
			final boolean succeed = response != null && response.data != null
					&& response.data.getBoolean(INTENT_KEY_SUCCEED);
			final Bundle extras = new Bundle();
			extras.putBoolean(INTENT_KEY_SUCCEED, succeed);
			if (shouldSetMinId()) {
				final long min_id = response != null && response.data != null ? response.data.getLong(
						INTENT_KEY_MIN_ID, -1) : -1;
				extras.putLong(INTENT_KEY_MIN_ID, min_id);
				mPreferences.edit().putLong(PREFERENCE_KEY_SAVED_MENTIONS_LIST_ID, min_id).commit();
			}
			mContext.sendBroadcast(new Intent(BROADCAST_MENTIONS_REFRESHED).putExtras(extras));
			super.onPostExecute(response);
		}

	}

	class StoreReceivedDirectMessagesTask extends StoreDirectMessagesTask {

		public StoreReceivedDirectMessagesTask(final List<TwitterListResponse<DirectMessage>> result,
				final boolean notify) {
			super(result, DirectMessages.Inbox.CONTENT_URI, notify, TASK_TAG_STORE_RECEIVED_DIRECT_MESSAGES);
		}

		@Override
		protected void onPostExecute(final SingleResponse<Bundle> response) {
			final boolean succeed = response != null && response.data != null
					&& response.data.getBoolean(INTENT_KEY_SUCCEED);
			mContext.sendBroadcast(new Intent(BROADCAST_RECEIVED_DIRECT_MESSAGES_REFRESHED).putExtra(
					INTENT_KEY_SUCCEED, succeed));
			super.onPostExecute(response);
		}

		@Override
		boolean isOutgoing() {
			return false;
		}

	}

	class StoreSentDirectMessagesTask extends StoreDirectMessagesTask {

		public StoreSentDirectMessagesTask(final List<TwitterListResponse<DirectMessage>> result, final boolean notify) {
			super(result, DirectMessages.Outbox.CONTENT_URI, notify, TASK_TAG_STORE_SENT_DIRECT_MESSAGES);
		}

		@Override
		protected void onPostExecute(final SingleResponse<Bundle> response) {
			final boolean succeed = response != null && response.data != null
					&& response.data.getBoolean(INTENT_KEY_SUCCEED);
			mContext.sendBroadcast(new Intent(BROADCAST_SENT_DIRECT_MESSAGES_REFRESHED).putExtra(INTENT_KEY_SUCCEED,
					succeed));
			super.onPostExecute(response);
		}

		@Override
		boolean isOutgoing() {
			return true;
		}

	}

	abstract class StoreStatusesTask extends ManagedAsyncTask<Void, Void, SingleResponse<Bundle>> {

		private final List<StatusListResponse> responses;
		private final Uri uri;
		private final boolean should_set_min_id;
		private final ArrayList<ContentValues> all_statuses = new ArrayList<ContentValues>();

		public StoreStatusesTask(final List<StatusListResponse> result, final Uri uri, final boolean should_set_min_id,
				final boolean notify, final String tag) {
			super(mContext, mAsyncTaskManager, tag);
			responses = result;
			this.should_set_min_id = should_set_min_id;
			this.uri = uri.buildUpon().appendQueryParameter(QUERY_PARAM_NOTIFY, String.valueOf(notify)).build();
		}

		public boolean shouldSetMinId() {
			return should_set_min_id;
		}

		@Override
		protected SingleResponse<Bundle> doInBackground(final Void... args) {
			boolean succeed = false;

			final ArrayList<Long> newly_inserted_ids = new ArrayList<Long>();
			for (final StatusListResponse response : responses) {
				final long account_id = response.account_id;
				final List<twitter4j.Status> statuses = response.list;
				if (statuses == null || statuses.size() <= 0) {
					continue;
				}
				final ArrayList<Long> ids_in_db = getStatusIdsInDatabase(mContext, uri, account_id);
				final boolean no_items_before = ids_in_db.size() <= 0;
				final List<ContentValues> values_list = new ArrayList<ContentValues>();
				final List<Long> status_ids = new ArrayList<Long>(), retweet_ids = new ArrayList<Long>();
				for (final twitter4j.Status status : statuses) {
					if (status == null) {
						continue;
					}
					final long status_id = status.getId();
					final long retweet_id = status.getRetweetedStatus() != null ? status.getRetweetedStatus().getId()
							: -1;

					status_ids.add(status_id);

					if ((retweet_id <= 0 || !retweet_ids.contains(retweet_id)) && !retweet_ids.contains(status_id)) {
						if (retweet_id > 0) {
							retweet_ids.add(retweet_id);
						}
						values_list.add(makeStatusContentValues(status, account_id, large_profile_image));
					}

				}

				// Delete all rows conflicting before new data inserted.

				final ArrayList<Long> account_newly_inserted = new ArrayList<Long>();
				account_newly_inserted.addAll(status_ids);
				account_newly_inserted.removeAll(ids_in_db);
				newly_inserted_ids.addAll(account_newly_inserted);
				final StringBuilder delete_where = new StringBuilder();
				final String ids_string = ListUtils.toString(status_ids, ',', true);
				delete_where.append(Statuses.ACCOUNT_ID + " = " + account_id);
				delete_where.append(" AND ");
				delete_where.append("(");
				delete_where.append(Statuses.STATUS_ID + " IN ( " + ids_string + " ) ");
				delete_where.append(" OR ");
				delete_where.append(Statuses.RETWEET_ID + " IN ( " + ids_string + " ) ");
				delete_where.append(")");
				final Uri delete_uri = appendQueryParameters(uri, new NameValuePairImpl(QUERY_PARAM_NOTIFY, false));
				final int rows_deleted = mResolver.delete(delete_uri, delete_where.toString(), null);
				// UCD
				final String UCD_new_status_ids = ListUtils.toString(account_newly_inserted, ',', true);
				ProfilingUtil.profile(mContext, account_id, "Download tweets, " + UCD_new_status_ids);
				all_statuses.addAll(values_list);
				// Insert previously fetched items.
				final Uri insert_query = appendQueryParameters(uri, new NameValuePairImpl(QUERY_PARAM_NEW_ITEMS_COUNT,
						newly_inserted_ids.size() - rows_deleted), new NameValuePairImpl(QUERY_PARAM_NOTIFY, false));
				mResolver.bulkInsert(insert_query, values_list.toArray(new ContentValues[values_list.size()]));

				// Insert a gap.
				// TODO make sure it will not have bugs.
				final long min_id = status_ids.size() > 0 ? Collections.min(status_ids) : -1;
				final boolean insert_gap = min_id > 0 && response.load_item_limit <= response.list.size()
						&& !no_items_before;
				if (insert_gap) {
					final ContentValues values = new ContentValues();
					values.put(Statuses.IS_GAP, 1);
					final StringBuilder where = new StringBuilder();
					where.append(Statuses.ACCOUNT_ID + " = " + account_id);
					where.append(" AND " + Statuses.STATUS_ID + " = " + min_id);
					final Uri update_uri = appendQueryParameters(uri, new NameValuePairImpl(QUERY_PARAM_NOTIFY, false));
					mResolver.update(update_uri, values, where.toString(), null);
					// Ignore gaps
					newly_inserted_ids.remove(min_id);
				}
				succeed = true;
			}
			final Bundle bundle = new Bundle();
			bundle.putBoolean(INTENT_KEY_SUCCEED, succeed);
			getAllStatusesIds(mContext, uri);
			if (should_set_min_id && newly_inserted_ids.size() > 0) {
				bundle.putLong(INTENT_KEY_MIN_ID, Collections.min(newly_inserted_ids));
			}
			return new TwitterSingleResponse<Bundle>(-1, bundle, null);
		}

		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			final StatusListResponse[] array = new StatusListResponse[responses.size()];
			new CacheUsersStatusesTask(mContext, responses.toArray(array)).execute();
		}

	}

	class StoreTrendsTask extends ManagedAsyncTask<Void, Void, SingleResponse<Bundle>> {

		private final ListResponse<Trends> response;
		private final Uri uri;

		public StoreTrendsTask(final ListResponse<Trends> result, final Uri uri) {
			super(mContext, mAsyncTaskManager, TASK_TAG_STORE_TRENDS);
			response = result;
			this.uri = uri;
		}

		@Override
		protected SingleResponse<Bundle> doInBackground(final Void... args) {
			final Bundle bundle = new Bundle();
			if (response != null) {

				final List<Trends> messages = response.list;
				final ArrayList<String> hashtags = new ArrayList<String>();
				final ArrayList<ContentValues> hashtag_values = new ArrayList<ContentValues>();
				if (messages != null && messages.size() > 0) {
					final ContentValues[] values_array = makeTrendsContentValues(messages);
					for (final ContentValues values : values_array) {
						final String hashtag = values.getAsString(CachedTrends.NAME).replaceFirst("#", "");
						if (hashtags.contains(hashtag)) {
							continue;
						}
						hashtags.add(hashtag);
						final ContentValues hashtag_value = new ContentValues();
						hashtag_value.put(CachedHashtags.NAME, hashtag);
						hashtag_values.add(hashtag_value);
					}
					mResolver.delete(uri, null, null);
					mResolver.bulkInsert(uri, values_array);
					mResolver.delete(CachedHashtags.CONTENT_URI,
							CachedHashtags.NAME + " IN (" + ListUtils.toStringForSQL(hashtags.size()) + ")",
							hashtags.toArray(new String[hashtags.size()]));
					mResolver.bulkInsert(CachedHashtags.CONTENT_URI,
							hashtag_values.toArray(new ContentValues[hashtag_values.size()]));
					bundle.putBoolean(INTENT_KEY_SUCCEED, true);
				}
			}
			return new TwitterSingleResponse<Bundle>(-1, bundle, null);
		}

		@Override
		protected void onPostExecute(final SingleResponse<Bundle> response) {
			if (response != null && response.data != null && response.data.getBoolean(INTENT_KEY_SUCCEED)) {
				final Intent intent = new Intent(BROADCAST_TRENDS_UPDATED);
				intent.putExtra(INTENT_KEY_SUCCEED, true);
				mContext.sendBroadcast(intent);
			}
			super.onPostExecute(response);

		}

	}

	class UpdateStatusTask extends ManagedAsyncTask<Void, Void, List<TwitterSingleResponse<twitter4j.Status>>> {

		private final ImageUploaderInterface uploader;
		private final TweetShortenerInterface shortener;

		private final Validator validator = new Validator();
		private final long[] account_ids;
		private final String content;

		private final ParcelableLocation location;
		private final Uri image_uri;
		private final long in_reply_to;
		private final boolean use_uploader, use_shortener, is_possibly_sensitive, delete_image;

		public UpdateStatusTask(final long[] account_ids, final String content, final ParcelableLocation location,
				final Uri image_uri, final long in_reply_to, final boolean is_possibly_sensitive,
				final boolean delete_image) {
			super(mContext, mAsyncTaskManager);
			final String uploader_component = mPreferences.getString(PREFERENCE_KEY_IMAGE_UPLOADER, null);
			final String shortener_component = mPreferences.getString(PREFERENCE_KEY_TWEET_SHORTENER, null);
			use_uploader = !isEmpty(uploader_component);
			final TwidereApplication app = TwidereApplication.getInstance(mContext);
			uploader = use_uploader ? ImageUploaderInterface.getInstance(app, uploader_component) : null;
			use_shortener = !isEmpty(shortener_component);
			shortener = use_shortener ? TweetShortenerInterface.getInstance(app, shortener_component) : null;
			this.account_ids = account_ids != null ? account_ids : new long[0];
			this.content = content;
			this.location = location;
			this.image_uri = image_uri;
			this.in_reply_to = in_reply_to;
			this.is_possibly_sensitive = is_possibly_sensitive;
			this.delete_image = delete_image;
		}

		@Override
		protected List<TwitterSingleResponse<twitter4j.Status>> doInBackground(final Void... params) {

			final Extractor extractor = new Extractor();
			final ArrayList<ContentValues> hashtag_values = new ArrayList<ContentValues>();
			final List<String> hashtags = extractor.extractHashtags(content);
			for (final String hashtag : hashtags) {
				final ContentValues values = new ContentValues();
				values.put(CachedHashtags.NAME, hashtag);
				hashtag_values.add(values);
			}
			mResolver.delete(CachedHashtags.CONTENT_URI,
					CachedHashtags.NAME + " IN (" + ListUtils.toStringForSQL(hashtags.size()) + ")",
					hashtags.toArray(new String[hashtags.size()]));
			mResolver.bulkInsert(CachedHashtags.CONTENT_URI,
					hashtag_values.toArray(new ContentValues[hashtag_values.size()]));

			final List<TwitterSingleResponse<twitter4j.Status>> result = new ArrayList<TwitterSingleResponse<twitter4j.Status>>();

			if (account_ids.length == 0) return result;

			try {
				if (use_uploader && uploader == null) throw new ImageUploaderNotFoundException();
				if (use_shortener && shortener == null) throw new TweetShortenerNotFoundException();

				final String image_path = getImagePathFromUri(mContext, image_uri);
				final File image_file = image_path != null ? new File(image_path) : null;

				final Uri upload_result_uri;
				try {
					if (uploader != null) {
						uploader.waitForService();
					}
					upload_result_uri = image_file != null && image_file.exists() && uploader != null ? uploader
							.upload(Uri.fromFile(image_file), content) : null;
				} catch (final Exception e) {
					throw new ImageUploadException();
				}
				if (use_uploader && image_file != null && image_file.exists() && upload_result_uri == null)
					throw new ImageUploadException();

				final String unshortened_content = use_uploader && upload_result_uri != null ? getImageUploadStatus(
						mContext, upload_result_uri.toString(), content) : content;

				final boolean should_shorten = unshortened_content != null && unshortened_content.length() > 0
						&& !validator.isValidTweet(unshortened_content);
				final String screen_name = getAccountScreenName(mContext, account_ids[0]);
				final String shortened_content;
				try {
					if (shortener != null) {
						shortener.waitForService();
					}
					shortened_content = should_shorten && use_shortener ? shortener.shorten(unshortened_content,
							screen_name, in_reply_to) : null;
				} catch (final Exception e) {
					throw new TweetShortenException();
				}

				if (should_shorten) {
					if (!use_shortener)
						throw new StatusTooLongException();
					else if (unshortened_content == null) throw new TweetShortenException();
				}

				final StatusUpdate status = new StatusUpdate(should_shorten && use_shortener ? shortened_content
						: unshortened_content);
				status.setInReplyToStatusId(in_reply_to);
				if (location != null) {
					status.setLocation(ParcelableLocation.toGeoLocation(location));
				}
				if (!use_uploader && image_file != null && image_file.exists()) {
					status.setMedia(image_file);
				}
				status.setPossiblySensitive(is_possibly_sensitive);

				for (final long account_id : account_ids) {
					final Twitter twitter = getTwitterInstance(mContext, account_id, false, true);
					if (twitter != null) {
						try {
							result.add(new TwitterSingleResponse<twitter4j.Status>(account_id, twitter
									.updateStatus(status), null));
						} catch (final TwitterException e) {
							result.add(new TwitterSingleResponse<twitter4j.Status>(account_id, null, e));
						}
					}
				}
			} catch (final UpdateStatusException e) {
				for (final long account_id : account_ids) {
					result.add(new TwitterSingleResponse<twitter4j.Status>(account_id, null, e));
				}
			}
			return result;
		}

		@Override
		protected void onCancelled() {
			saveDrafts(ListUtils.fromArray(account_ids));
			super.onCancelled();
		}

		@Override
		protected void onPostExecute(final List<TwitterSingleResponse<twitter4j.Status>> result) {

			boolean succeed = true;
			Exception exception = null;
			final List<Long> failed_account_ids = new ArrayList<Long>();

			for (final TwitterSingleResponse<twitter4j.Status> response : result) {
				if (response.data == null) {
					succeed = false;
					failed_account_ids.add(response.account_id);
					if (exception == null) {
						exception = response.exception;
					}
				}
			}
			if (succeed) {
				Toast.makeText(mContext, R.string.send_successful, Toast.LENGTH_SHORT).show();
				if (image_uri != null && delete_image) {
					final String path = getImagePathFromUri(mContext, image_uri);
					if (path != null) {
						new File(path).delete();
					}
				}
			} else {
				// If the status is a duplicate, there's no need to save it to
				// drafts.
				if (exception instanceof TwitterException && ((TwitterException) exception).getErrorCode() == 187) {
					Utils.showErrorToast(mContext, mContext.getString(R.string.status_is_duplicate), false);
				} else {
					saveDrafts(failed_account_ids);
					showErrorToast(R.string.sending_status, exception, true);
				}
			}
			super.onPostExecute(result);
			if (mPreferences.getBoolean(PREFERENCE_KEY_REFRESH_AFTER_TWEET, false)) {
				refreshAll();
			}
		}

		private void saveDrafts(final List<Long> account_ids) {
			final ContentValues values = new ContentValues();
			values.put(Drafts.ACCOUNT_IDS, ListUtils.toString(account_ids, ';', false));
			values.put(Drafts.IN_REPLY_TO_STATUS_ID, in_reply_to);
			values.put(Drafts.TEXT, content);
			if (image_uri != null) {
				values.put(Drafts.IS_IMAGE_ATTACHED, !delete_image);
				values.put(Drafts.IS_PHOTO_ATTACHED, delete_image);
				values.put(Drafts.IMAGE_URI, parseString(image_uri));
			}
			mResolver.insert(Drafts.CONTENT_URI, values);
			final String title = mContext.getString(R.string.tweet_not_sent);
			final String message = mContext.getString(R.string.tweet_not_sent_summary);
			final Intent intent = new Intent(INTENT_ACTION_DRAFTS);
			final Notification notification = buildNotification(title, message, R.drawable.ic_stat_tweet, intent, null);
			mNotificationManager.notify(NOTIFICATION_ID_DRAFTS, notification);
		}

		class ImageUploaderNotFoundException extends UpdateStatusException {
			private static final long serialVersionUID = 1041685850011544106L;

			public ImageUploaderNotFoundException() {
				super(R.string.error_message_image_uploader_not_found);
			}
		}

		class ImageUploadException extends UpdateStatusException {
			private static final long serialVersionUID = 8596614696393917525L;

			public ImageUploadException() {
				super(R.string.error_message_image_upload_failed);
			}
		}

		class StatusTooLongException extends UpdateStatusException {
			private static final long serialVersionUID = -6469920130856384219L;

			public StatusTooLongException() {
				super(R.string.error_message_status_too_long);
			}
		}

		class TweetShortenerNotFoundException extends UpdateStatusException {
			private static final long serialVersionUID = -7262474256595304566L;

			public TweetShortenerNotFoundException() {
				super(R.string.error_message_tweet_shortener_not_found);
			}
		}

		class TweetShortenException extends UpdateStatusException {
			private static final long serialVersionUID = 3075877185536740034L;

			public TweetShortenException() {
				super(R.string.error_message_tweet_shorten_failed);
			}
		}

		class UpdateStatusException extends Exception {
			private static final long serialVersionUID = -1267218921727097910L;

			public UpdateStatusException(final int message) {
				super(mContext.getString(message));
			}
		}
	}

	class UpdateUserListDetailsTask extends ManagedAsyncTask<Void, Void, SingleResponse<UserList>> {

		private final long account_id;

		private final int list_id;

		private final boolean is_public;
		private final String name, description;

		public UpdateUserListDetailsTask(final long account_id, final int list_id, final boolean is_public,
				final String name, final String description) {
			super(mContext, mAsyncTaskManager);
			this.account_id = account_id;
			this.name = name;
			this.list_id = list_id;
			this.is_public = is_public;
			this.description = description;
		}

		@Override
		protected SingleResponse<UserList> doInBackground(final Void... params) {

			final Twitter twitter = getTwitterInstance(mContext, account_id, false);
			if (twitter != null) {
				try {
					final UserList user = twitter.updateUserList(list_id, name, is_public, description);
					return new TwitterSingleResponse<UserList>(account_id, user, null);
				} catch (final TwitterException e) {
					return new TwitterSingleResponse<UserList>(account_id, null, e);
				}
			}
			return new TwitterSingleResponse<UserList>(account_id, null, null);
		}

		@Override
		protected void onPostExecute(final SingleResponse<UserList> result) {
			final Intent intent = new Intent(BROADCAST_USER_LIST_DETAILS_UPDATED);
			intent.putExtra(INTENT_KEY_LIST_ID, list_id);
			if (result != null && result.data != null && result.data.getId() > 0) {
				Toast.makeText(mContext, R.string.profile_update_successful, Toast.LENGTH_SHORT).show();
				intent.putExtra(INTENT_KEY_SUCCEED, true);
			} else {
				showErrorToast(R.string.updating_details, result.exception, true);
			}
			mContext.sendBroadcast(intent);
			super.onPostExecute(result);
		}

	}

}
