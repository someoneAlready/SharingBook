package com.example.sharingbook;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Set;

import libcore.io.DiskLruCache;
import libcore.io.DiskLruCache.Snapshot;

import org.json.JSONArray;
import org.json.JSONObject;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.AdapterView.OnItemClickListener;

import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

public class FanList extends Activity {
	ListView listview = null;
	ProgressBar progressbar = null;
	RequestQueue mQueue = null;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.me);
		listview = (ListView) findViewById(R.id.listview);
		progressbar = (ProgressBar) findViewById(R.id.loading_spinner);
		listview.setVisibility(View.GONE);
		updateObserving();
	}

	void updateObserving() {
		String ustuid = tool.getString(this, "ustuid");
		String webServer = getResources().getString(R.string.webServer);
		final RequestQueue mQueue = Volley.newRequestQueue(this);

		JsonObjectRequest jsonReq = new JsonObjectRequest(webServer
				+ "/observingList.php?type=1&ustuid=" + ustuid, null,
				new Response.Listener<JSONObject>() {
					@Override
					public void onResponse(JSONObject response) {
						try {
							JSONArray jsonArray = response
									.getJSONArray("dataList");
							setListView(jsonArray);
							crossfade();
						} catch (Exception e) {

						}
					}

				}, new Response.ErrorListener() {
					@Override
					public void onErrorResponse(VolleyError error) {

					}

				});
		mQueue.add(jsonReq);
	}

	void setListView(JSONArray jsonArray) {
		MyAdapter ad = new MyAdapter(this, jsonArray);
		listview.setAdapter(ad);
		listview.setOnItemClickListener(new ListListener(jsonArray));
	}

	public class ListListener implements OnItemClickListener {
		JSONArray jsonArray;

		ListListener(JSONArray jsonArray) {
			this.jsonArray = jsonArray;
		}

		@Override
		public void onItemClick(AdapterView<?> parent, View view, int position,
				long id) {
			try {
				JSONObject bookInfo = jsonArray.getJSONObject(position + 1);
				
				Intent intent = new Intent(FanList.this, OtherUser.class);
				intent.putExtra("ustuid", bookInfo.getString("ustuid"));
				intent.putExtra("uname", bookInfo.getString("uname"));		
				startActivity(intent);
				
			} catch (Exception e) {
			}
		}

	}
	
	class MyAdapter extends BaseAdapter {
		JSONArray jsonArray;
		LayoutInflater mLayoutInflater;
		private Set<BitmapWorkerTask> taskCollection;
		private LruCache<String, Bitmap> mMemoryCache;
		private DiskLruCache mDiskLruCache;

		public MyAdapter(Context context, JSONArray jsonA) {
			jsonArray = jsonA;
			mLayoutInflater = (LayoutInflater) context
					.getSystemService(context.LAYOUT_INFLATER_SERVICE);
			taskCollection = new HashSet<BitmapWorkerTask>();
			int maxMemory = (int) Runtime.getRuntime().maxMemory();
			int cacheSize = maxMemory / 14;

			try {
				File cacheDir = getDiskCacheDir(context, "thumb");
				if (!cacheDir.exists()) {
					cacheDir.mkdirs();
				}

				mDiskLruCache = DiskLruCache.open(cacheDir,
						getAppVersion(context), 1, 20 * 1024 * 1024);
			} catch (IOException e) {

			}

			mMemoryCache = new LruCache<String, Bitmap>(cacheSize) {
				@Override
				protected int sizeOf(String key, Bitmap bitmap) {
					return bitmap.getByteCount();
				}
			};

		}

		@Override
		public int getCount() {
			return jsonArray.length() - 1;
		}

		@Override
		public Object getItem(int position) {
			try {
				if (position == 0)
					return null;
				return jsonArray.getJSONObject(position + 1);
			} catch (Exception e) {
				return null;
			}
		}

		// itemId
		@Override
		public long getItemId(int position) {
			return position;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			View view;
			if (convertView == null) {
				view = mLayoutInflater.inflate(R.layout.me_item_image, null);
			} else {
				view = convertView;
			}
			ImageView upic = (ImageView) view.findViewById(R.id.upic);
			TextView uname = (TextView) view.findViewById(R.id.uname);

			try {
				JSONObject user = jsonArray.getJSONObject(position + 1);
				uname.setText(user.getString("uname"));
				String url = user.getString("upic");
				upic.setTag(url);
				loadBitmaps(upic, url);
			} catch (Exception e) {

			}

			return view;
		}

		public void addBitmapToMemoryCache(String key, Bitmap bitmap) {
			if (getBitmapFromMemoryCache(key) == null) {
				mMemoryCache.put(key, bitmap);
			}
		}

		/**
		 * 从LruCache中获取一张图片，如果不存在就返回null。
		 * 
		 * @param key
		 *            LruCache的键，这里传入图片的URL地址。
		 * @return 对应传入键的Bitmap对象，或者null。
		 */
		public Bitmap getBitmapFromMemoryCache(String key) {

			return mMemoryCache.get(key);
		}

		/**
		 * 加载Bitmap对象。此方法会在LruCache中检查所有屏幕中可见的ImageView的Bitmap对象，
		 * 如果发现任何一个ImageView的Bitmap对象不在缓存中，就会开启异步线程去下载图片。
		 */
		public void loadBitmaps(ImageView imageView, String imageUrl) {
			try {
				Bitmap bitmap = getBitmapFromMemoryCache(imageUrl);
				if (bitmap == null) {
					BitmapWorkerTask task = new BitmapWorkerTask();
					taskCollection.add(task);
					task.execute(imageUrl);
				} else {

					if (imageView != null && bitmap != null) {
						Bitmap bpSmall = tool.bitmapChange(bitmap, (float) 0.9);
						imageView.setImageBitmap(bpSmall);
					}
				}
			} catch (Exception e) {
				// show(e.toString());
				// e.printStackTrace();
			}
		}

		/**
		 * 取消所有正在下载或等待下载的任务。
		 */
		public void cancelAllTasks() {
			if (taskCollection != null) {
				for (BitmapWorkerTask task : taskCollection) {
					task.cancel(false);
				}
			}
		}

		/**
		 * 将缓存记录同步到journal文件中。
		 */
		public void fluchCache() {
			if (mDiskLruCache != null) {
				try {
					mDiskLruCache.flush();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}

		class BitmapWorkerTask extends AsyncTask<String, Void, Bitmap> {

			/**
			 * 图片的URL地址
			 */
			private String imageUrl;

			@Override
			protected Bitmap doInBackground(String... params) {
				imageUrl = params[0];
				FileDescriptor fileDescriptor = null;
				FileInputStream fileInputStream = null;
				Snapshot snapShot = null;
				try {
					// 生成图片URL对应的key
					final String key = hashKeyForDisk(imageUrl);
					// 查找key对应的缓存
					snapShot = mDiskLruCache.get(key);
					if (snapShot == null) {
						// 如果没有找到对应的缓存，则准备从网络上请求数据，并写入缓存
						DiskLruCache.Editor editor = mDiskLruCache.edit(key);
						if (editor != null) {
							OutputStream outputStream = editor
									.newOutputStream(0);
							if (downloadUrlToStream(imageUrl, outputStream)) {
								editor.commit();
							} else {
								editor.abort();
							}
						}
						// 缓存被写入后，再次查找key对应的缓存
						snapShot = mDiskLruCache.get(key);
					}
					if (snapShot != null) {
						fileInputStream = (FileInputStream) snapShot
								.getInputStream(0);
						fileDescriptor = fileInputStream.getFD();
					}
					// 将缓存数据解析成Bitmap对象
					Bitmap bitmap = null;
					if (fileDescriptor != null) {
						bitmap = BitmapFactory
								.decodeFileDescriptor(fileDescriptor);
					}
					if (bitmap != null) {
						// 将Bitmap对象添加到内存缓存当中
						addBitmapToMemoryCache(params[0], bitmap);
					}
					return bitmap;
				} catch (IOException e) {
					e.printStackTrace();
				} finally {
					if (fileDescriptor == null && fileInputStream != null) {
						try {
							fileInputStream.close();
						} catch (IOException e) {
						}
					}
				}
				return null;
			}

			@Override
			protected void onPostExecute(Bitmap bitmap) {
				super.onPostExecute(bitmap);
				// 根据Tag找到相应的ImageView控件，将下载好的图片显示出来。

				ImageView imageView = (ImageView) listview
						.findViewWithTag(imageUrl);

				if (imageView != null && bitmap != null) {
					Bitmap bpSmall = tool.bitmapChange(bitmap, (float) 0.9);
					imageView.setImageBitmap(bpSmall);
				}
				taskCollection.remove(this);
			}

			/**
			 * 建立HTTP请求，并获取Bitmap对象。
			 * 
			 * @param imageUrl
			 *            图片的URL地址
			 * @return 解析后的Bitmap对象
			 */
			private boolean downloadUrlToStream(String urlString,
					OutputStream outputStream) {
				HttpURLConnection urlConnection = null;
				BufferedOutputStream out = null;
				BufferedInputStream in = null;
				try {
					final URL url = new URL(urlString);
					urlConnection = (HttpURLConnection) url.openConnection();
					in = new BufferedInputStream(
							urlConnection.getInputStream(), 8 * 1024);
					out = new BufferedOutputStream(outputStream, 8 * 1024);
					int b;
					while ((b = in.read()) != -1) {
						out.write(b);
					}
					return true;
				} catch (final IOException e) {
					e.printStackTrace();
				} finally {
					if (urlConnection != null) {
						urlConnection.disconnect();
					}
					try {
						if (out != null) {
							out.close();
						}
						if (in != null) {
							in.close();
						}
					} catch (final IOException e) {
						// e.printStackTrace();
					}
				}
				return false;
			}

		}

	}

	public File getDiskCacheDir(Context context, String uniqueName) {
		String cachePath;
		if (Environment.MEDIA_MOUNTED.equals(Environment
				.getExternalStorageState())
				|| !Environment.isExternalStorageRemovable()) {
			cachePath = context.getExternalCacheDir().getPath();
		} else {
			cachePath = context.getCacheDir().getPath();
		}
		return new File(cachePath + File.separator + uniqueName);
	}

	/**
	 * 获取当前应用程序的版本号。
	 */
	public int getAppVersion(Context context) {
		try {
			PackageInfo info = context.getPackageManager().getPackageInfo(
					context.getPackageName(), 0);
			return info.versionCode;
		} catch (NameNotFoundException e) {
			e.printStackTrace();
		}
		return 1;
	}

	public String hashKeyForDisk(String key) {
		String cacheKey;
		try {
			final MessageDigest mDigest = MessageDigest.getInstance("MD5");
			mDigest.update(key.getBytes());
			cacheKey = bytesToHexString(mDigest.digest());
		} catch (NoSuchAlgorithmException e) {
			cacheKey = String.valueOf(key.hashCode());
		}
		return cacheKey;
	}

	private String bytesToHexString(byte[] bytes) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < bytes.length; i++) {
			String hex = Integer.toHexString(0xFF & bytes[i]);
			if (hex.length() == 1) {
				sb.append('0');
			}
			sb.append(hex);
		}
		return sb.toString();
	}

	private void crossfade() {
		int mShortAnimationDuration = getResources().getInteger(
				android.R.integer.config_shortAnimTime);

		listview.setAlpha(0f);
		listview.setVisibility(View.VISIBLE);

		listview.animate().alpha(1f).setDuration(mShortAnimationDuration)
				.setListener(null);

		progressbar.animate().alpha(0f).setDuration(mShortAnimationDuration)
				.setListener(new AnimatorListenerAdapter() {
					@Override
					public void onAnimationEnd(Animator animation) {
						progressbar.setVisibility(View.GONE);
					}
				});
	}

	public void show(String s) {
		new AlertDialog.Builder(this).setMessage(s)
				.setPositiveButton(R.string.confirm, null).show();
	}

}
