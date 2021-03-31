package com.vincent_falzon.discreetlauncher ;

// License
/*

	This file is part of Discreet Launcher.

	Copyright (C) 2019-2021 Vincent Falzon

	This program is free software: you can redistribute it and/or modify
	it under the terms of the GNU General Public License as published by
	the Free Software Foundation, either version 3 of the License, or
	(at your option) any later version.

	This program is distributed in the hope that it will be useful,
	but WITHOUT ANY WARRANTY; without even the implied warranty of
	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
	GNU General Public License for more details.

	You should have received a copy of the GNU General Public License
	along with this program.  If not, see <https://www.gnu.org/licenses/>.

 */

// Imports
import android.content.Context ;
import android.content.Intent ;
import android.content.SharedPreferences ;
import android.content.pm.PackageManager ;
import android.content.pm.ResolveInfo ;
import android.graphics.drawable.Drawable ;
import androidx.core.content.res.ResourcesCompat ;
import androidx.preference.PreferenceManager ;
import java.text.SimpleDateFormat ;
import java.util.ArrayList ;
import java.util.Collections ;
import java.util.Comparator ;
import java.util.Date ;
import java.util.List ;

/**
 * Contain applications lists (complete and favorites) and the last update timestamp.
 */
class ApplicationsList
{
	// Constants
	public static final String FAVORITES_FILE = "favorites.txt" ;
	public static final String SHORTCUTS_FILE = "shortcuts.txt" ;
	public static final String SHORTCUTS_LEGACY_FILE = "shortcuts_legacy.txt" ;

	// Attributes
	private final ArrayList<Application> applications ;
	private final ArrayList<Application> favorites ;
	private final Application[] notificationApps ;
	private String last_update ;
	private boolean update_in_progress ;


	/**
	 * Constructor to create the applications list.
	 */
	ApplicationsList()
	{
		applications = new ArrayList<>() ;
		favorites = new ArrayList<>() ;
		notificationApps = new Application[3] ;
		last_update = "" ;
		update_in_progress = false ;
	}


	/**
	 * Update both the complete applications list and the favorite applications list.
	 * @param context To get the package manager, load icon pack and display a toast
	 */
	void update(Context context)
	{
		// Check if an update is already in progress
		if(update_in_progress) return ;
		update_in_progress = true ;

		// Initializations
		PackageManager apkManager = context.getPackageManager() ;
		applications.clear() ;

		// Retrieve the list of applications that can be launched by the user
		Intent intent = new Intent(Intent.ACTION_MAIN) ;
		intent.addCategory(Intent.CATEGORY_LAUNCHER) ;
		List<ResolveInfo> apkManagerList = apkManager.queryIntentActivities(intent, 0) ;

		// Define the icons size in pixels
		int icon_size = Math.round(48 * context.getResources().getDisplayMetrics().density) ;

		// If an icon pack is selected, load it
		IconPack iconPack = loadIconPack(context) ;

		// Browse the APK manager list and store the data of each application in the main list
		Drawable icon ;
		for(ResolveInfo entry : apkManagerList)
		{
			// Load the application icon
			if(iconPack == null) icon = entry.loadIcon(apkManager) ;
				else
				{
					// Retrieve the icon in the pack, use the real icon if not found
					icon = iconPack.searchIcon(entry.activityInfo.packageName, entry.activityInfo.name) ;
					if(icon == null) icon = entry.loadIcon(apkManager) ;
				}
			icon.setBounds(0, 0, icon_size, icon_size) ;

			// Add the application to the list
			Application application = new Application(
					entry.loadLabel(apkManager).toString(),
					entry.activityInfo.name,
					entry.activityInfo.packageName,
					icon) ;
			applications.add(application) ;
		}

		// Add the shortcuts to the list as applications
		loadShortcuts(context) ;

		// Sort the applications list in alphabetic order based on display name
		Collections.sort(applications, new Comparator<Application>()
		{
			@Override
			public int compare(Application application1, Application application2)
			{
				return application1.getDisplayName().compareToIgnoreCase(application2.getDisplayName()) ;
			}
		}) ;

		// Update the favorites applications list
		updateFavorites(context) ;

		// Save the last update timestamp and inform the user that the list has been refreshed
		last_update = SimpleDateFormat.getDateTimeInstance().format(new Date()) ;
		ShowDialog.toast(context, R.string.text_applications_list_refreshed) ;
		update_in_progress = false ;
	}


	/**
	 * Update the favorites applications list based on the favorites file and the complete list.
	 * @param context To get the file path
	 */
	void updateFavorites(Context context)
	{
		// Initializations
		favorites.clear() ;
		InternalFile file = new InternalFile(context, FAVORITES_FILE) ;
		if(file.isNotExisting()) return ;

		// Retrieve and browse the internal names of all favorites applications
		for(String name : file.readAllLines())
		{
			// Search the internal name in the applications list
			for(Application application : applications)
				if(application.getName().equals(name))
				{
					// Add the application to the favorites and move to the next line
					favorites.add(application) ;
					break ;
				}
		}

		// To remove later: manage old file format
		if(favorites.size() == 0) convertOldFavoritesFileFormat(context, file) ;
	}


	/**
	 * To be removed later, file format conversion due to change in v1.2.0.
	 * This method is called during the favorites update in case the user updated the launcher from
	 * a version before v1.2.0. It updates the favorites file to new format with internal names.
	 * @param context To display an alert dialog
	 */
	private void convertOldFavoritesFileFormat(Context context, InternalFile file)
	{
		// Retrieve and browse the package names of all favorites applications
		for(String apk : file.readAllLines())
		{
			// Search the package name in the applications list
			for(Application application : applications)
				if(application.getApk().equals(apk))
				{
					// Add the application to the favorites and move to the next line
					favorites.add(application) ;
					break ;
				}
		}

		// Check if favorites need to be migrated
		if(favorites.size() > 0)
		{
			// Try to migrate them to the new format
			if(file.hasRemovalFailed(context)) return ;
			for(Application application : favorites)
				if(!file.writeLine(application.getName())) return ;
			ShowDialog.alert(context, context.getString(R.string.error_file_format_changed)) ;
		}
	}


	/**
	 * Check if an icon pack is selected and load it if it does.
	 * @param context To get the settings and display alerts
	 * @return An icon pack loaded or <code>null</code> if none is selected
	 */
	private IconPack loadIconPack(Context context)
	{
		// Check if an icon pack is selected
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext()) ;
		String pack_name = settings.getString(ActivitySettings.ICON_PACK, ActivitySettings.NONE) ;
		if((pack_name == null) || pack_name.equals(ActivitySettings.NONE)) return null ;

		// Try to load the icon pack resources
		IconPack iconPack = new IconPack(context, pack_name) ;
		if(!iconPack.loadResources())
			{
				// Display an error message and set the icon pack to none
				ShowDialog.alert(context, context.getString(R.string.error_application_not_found, pack_name)) ;
				SharedPreferences.Editor editor = settings.edit() ;
				editor.putString(ActivitySettings.ICON_PACK, ActivitySettings.NONE).apply() ;
				return null ;
			}

		// Try to find the resource ID of the appfilter.xml file in the icon pack
		if(!iconPack.findAppfilterID())
			{
				// Display an error message and do not use the icon pack
				ShowDialog.alert(context, context.getString(R.string.error_appfilter_not_found, pack_name)) ;
				return null ;
			}

		// Return the icon pack loaded
		return iconPack ;
	}


	/**
	 * Update the applications used as notification buttons
	 * @param context To get the settings
	 */
	void updateNotificationApps(Context context)
	{
		// Initializations
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext()) ;
		String[] setting_app = new String[3] ;

		// Update the notification applications
		String[] app ;
		for(int i = 0 ; i < 3 ; i++)
		{
			// Check if an application has been selected
			setting_app[i] = settings.getString(ActivitySettings.NOTIFICATION_APP + (i + 1), ActivitySettings.NONE) ;
			if((setting_app[i] == null) || setting_app[i].equals(ActivitySettings.NONE))
				{
					notificationApps[i] = null ;
					continue ;
				}

			// Retrieve the applications details
			app = setting_app[i].split(Application.NOTIFICATION_SEPARATOR) ;
			if(app.length == 3)	notificationApps[i] = new Application(app[0], app[1], app[2], null) ;
				else notificationApps[i] = null ;
		}
	}


	/**
	 * Add shortcuts to the applications list based on the shortcuts files.
	 * @param context To get the file path and generic icon
	 */
	void loadShortcuts(Context context)
	{
		// Use the notification icon as generic shortcut icon
		Drawable icon = ResourcesCompat.getDrawable(context.getResources(), R.drawable.notification_icon, null) ;
		int icon_size = Math.round(48 * context.getResources().getDisplayMetrics().density) ;
		if(icon != null) icon.setBounds(0, 0, icon_size, icon_size) ;

		// If their file exists, browse the shortcuts
		InternalFile file = new InternalFile(context, SHORTCUTS_FILE) ;
		if(!file.isNotExisting())
			{
				String[] shortcut ;
				for(String shortcut_line : file.readAllLines())
				{
					// Add the shortcut to the list of applications
					shortcut = shortcut_line.split(Application.SHORTCUT_SEPARATOR) ;
					if(shortcut.length != 4) continue ;
					applications.add(new Application(shortcut[0],
							shortcut[1] + Application.SHORTCUT_SEPARATOR + shortcut[2] + Application.SHORTCUT_SEPARATOR + shortcut[3],
							Application.APK_SHORTCUT, icon)) ;
				}
			}

		// If their file exists, browse the legacy shortcuts
		InternalFile legacyFile = new InternalFile(context, SHORTCUTS_LEGACY_FILE) ;
		if(!legacyFile.isNotExisting())
			{
				String[] legacy_shortcut ;
				for(String legacy_shortcut_line : legacyFile.readAllLines())
				{
					// Add the shortcut to the list of applications
					legacy_shortcut = legacy_shortcut_line.split(Application.SHORTCUT_SEPARATOR) ;
					if(legacy_shortcut.length != 2) continue ;
					applications.add(new Application(legacy_shortcut[0], legacy_shortcut[1], Application.APK_SHORTCUT_LEGACY, icon)) ;
				}
			}
	}


	/**
	 * Method called when a request to add a shortcut has been received.
	 * @param context Provided by the receiver
	 * @param display_name Displayed to the user
	 * @param shortcut Line to add to the shortcuts file
	 * @param legacy <code>true</code> if before Oreo, <code>false</code> otherwise
	 */
	void addShortcut(Context context, String display_name, String shortcut, boolean legacy)
	{
		// Check if the shortcut already exists in the file
		InternalFile file ;
		if(legacy) file = new InternalFile(context, SHORTCUTS_LEGACY_FILE) ;
			else file = new InternalFile(context, SHORTCUTS_FILE) ;
		if(!file.isNotExisting())
			{
				// Browse all the saved shortcuts
				String[] saved_shortcut ;
				for(String shortcut_line : file.readAllLines())
				{
					// Do not continue if the shortcut already exists
					saved_shortcut = shortcut_line.split(Application.SHORTCUT_SEPARATOR) ;
					if(display_name.equals(saved_shortcut[0])) return ;
				}
			}

		// If it was not existing, add the shortcut to the file
		if(!file.writeLine(shortcut))
			{
				ShowDialog.alert(context, context.getString(R.string.error_with_shortcut, display_name)) ;
				return ;
			}

		// Update the applications list
		update(context) ;
	}


	/**
	 * Remove an entry from the shortcuts file and update the applications list
	 * @param context To get the file path
	 * @param removedShortcut The shortcut to remove
	 */
	void removeShortcut(Context context, Application removedShortcut)
	{
		// Save the current shortcuts list and remove the file
		InternalFile file ;
		if(removedShortcut.getApk().equals(Application.APK_SHORTCUT_LEGACY))
				file = new InternalFile(context, SHORTCUTS_LEGACY_FILE) ;
			else file = new InternalFile(context, SHORTCUTS_FILE) ;
		ArrayList<String> currentShortcuts = file.readAllLines() ;
		if(file.hasRemovalFailed(context)) return ;

		// Write the new shortcuts list in the file
		String to_remove = removedShortcut.getDisplayName() ;
		String[] shortcut ;
		for(String shortcut_line : currentShortcuts)
		{
			// Extract the display name from the line and check if this is the shortcut to remove
			shortcut = shortcut_line.split(Application.SHORTCUT_SEPARATOR) ;
			if(shortcut[0].equals(to_remove)) continue ;

			// Add all the other shortcuts to the list again
			if(!file.writeLine(shortcut_line))
				{
					ShowDialog.toastLong(context, context.getString(R.string.error_with_shortcut, shortcut[0])) ;
					return ;
				}
		}

		// Update the applications list
		update(context) ;
	}


	/**
	 * Return the complete list of applications.
	 * @return For display in the Drawer activity
	 */
	ArrayList<Application> getApplications()
	{
		return applications ;
	}


	/**
	 * Return the list of favorites applications.
	 * @return For display in the favorites panel
	 */
	ArrayList<Application> getFavorites()
	{
		return favorites ;
	}


	/**
	 * Return the timestamp of the last time the applications list was updated.
	 * @return Date and time in text format
	 */
	String getLastUpdate()
	{
		return last_update ;
	}


	/**
	 * Return the number of favorites applications in the list.
	 * @return Number of favorites applications
	 */
	int getFavoritesCount()
	{
		return favorites.size() ;
	}


	/**
	 * Return the three notification applications
	 * @return For display as notification buttons
	 */
	Application[] getNotificationApps()
	{
		return notificationApps ;
	}
}