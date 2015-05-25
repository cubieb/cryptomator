/*******************************************************************************
 * Copyright (c) 2014 Sebastian Stenzel, Markus Kreusch
 * This file is licensed under the terms of the MIT license.
 * See the LICENSE.txt file for more info.
 * 
 * Contributors:
 *     Sebastian Stenzel - initial API and implementation, strategy fine tuning
 *     Markus Kreusch - Refactored WebDavMounter to use strategy pattern
 ******************************************************************************/
package org.cryptomator.ui.util.mount;

import static org.cryptomator.ui.util.command.Script.fromLines;

import java.net.URI;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.SystemUtils;
import org.cryptomator.ui.util.command.CommandResult;
import org.cryptomator.ui.util.command.Script;

/**
 * A {@link WebDavMounterStrategy} utilizing the "net use" command.
 * <p>
 * Tested on Windows 7 but should also work on Windows 8.
 *
 * @author Markus Kreusch
 */
final class WindowsWebDavMounter implements WebDavMounterStrategy {

	private static final Pattern WIN_MOUNT_DRIVELETTER_PATTERN = Pattern.compile("\\s*([A-Z]:)\\s*");
	private static final int MAX_MOUNT_ATTEMPTS = 3;

	@Override
	public boolean shouldWork() {
		return SystemUtils.IS_OS_WINDOWS;
	}

	@Override
	public void warmUp(int serverPort) {
		
	}

	@Override
	public WebDavMount mount(URI uri, String name) throws CommandFailedException {
        final Script proxyBypassCmd = fromLines("reg add \"HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings\" /v \"ProxyOverride\" /d \"<local>;0--1.ipv6-literal.net;0--1.ipv6-literal.net:%PORT%\" /f");
        proxyBypassCmd.addEnv("PORT", String.valueOf(uri.getPort()));
		final Script mountScript = fromLines("net use * \\\\0--1.ipv6-literal.net@%DAV_PORT%\\DavWWWRoot%DAV_UNC_PATH% /persistent:no");
		System.err.println(mountScript.getLines()[0]);
		mountScript.addEnv("DAV_PORT", String.valueOf(uri.getPort())).addEnv("DAV_UNC_PATH", uri.getRawPath().replace('/', '\\'));
		String driveLetter = null;
		// The ugliness of the following 20 lines is solely windows' fault. Deal with it.
		for (int i = 0; i < MAX_MOUNT_ATTEMPTS; i++) {
			try {
				proxyBypassCmd.execute();
				final CommandResult mountResult = mountScript.execute(5, TimeUnit.SECONDS);
				driveLetter = getDriveLetter(mountResult.getStdOut());
				break;
			} catch (CommandFailedException ex) {
				if (i == MAX_MOUNT_ATTEMPTS - 1) {
					throw ex;
				} else {
					try {
						// retry after 5s
						Thread.sleep(5000);
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
					}
				}
			}
		}
		final Script openExplorerScript = fromLines("start explorer.exe " + driveLetter);
		openExplorerScript.execute();
		final Script unmountScript = fromLines("net use " + driveLetter + " /delete").addEnv("DRIVE_LETTER", driveLetter);
		final String finalDriveLetter = driveLetter;
		return new AbstractWebDavMount() {
			@Override
			public void unmount() throws CommandFailedException {
				// only attempt unmount if user didn't unmount manually:
				if (Files.exists(FileSystems.getDefault().getPath(finalDriveLetter))) {
					unmountScript.execute();
				}
			}
		};
	}

	private String getDriveLetter(String result) throws CommandFailedException {
		final Matcher matcher = WIN_MOUNT_DRIVELETTER_PATTERN.matcher(result);
		if (matcher.find()) {
			return matcher.group(1);
		} else {
			throw new CommandFailedException("Failed to get a drive letter from net use output.");
		}
	}

}
