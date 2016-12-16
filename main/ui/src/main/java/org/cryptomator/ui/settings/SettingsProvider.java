/*******************************************************************************
 * Copyright (c) 2016 Sebastian Stenzel and others.
 * This file is licensed under the terms of the MIT license.
 * See the LICENSE.txt file for more info.
 *
 * Contributors:
 *     Sebastian Stenzel - initial API and implementation
 *******************************************************************************/
package org.cryptomator.ui.settings;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.cryptomator.common.LazyInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

@Singleton
public class SettingsProvider implements Provider<Settings> {

	private static final Logger LOG = LoggerFactory.getLogger(SettingsProvider.class);
	private static final Path DEFAULT_SETTINGS_PATH;
	private static final long SAVE_DELAY_MS = 1000;

	static {
		final FileSystem fs = FileSystems.getDefault();
		if (SystemUtils.IS_OS_WINDOWS) {
			DEFAULT_SETTINGS_PATH = fs.getPath(SystemUtils.USER_HOME, "AppData/Roaming/Cryptomator/settings.json");
		} else if (SystemUtils.IS_OS_MAC_OSX) {
			DEFAULT_SETTINGS_PATH = fs.getPath(SystemUtils.USER_HOME, "Library/Application Support/Cryptomator/settings.json");
		} else {
			DEFAULT_SETTINGS_PATH = fs.getPath(SystemUtils.USER_HOME, ".Cryptomator/settings.json");
		}
	}

	private final ScheduledExecutorService saveScheduler = Executors.newSingleThreadScheduledExecutor();
	private final AtomicReference<ScheduledFuture<?>> scheduledSaveCmd = new AtomicReference<>();
	private final AtomicReference<Settings> settings = new AtomicReference<>();
	private final SettingsInstanceCreator settingsInstanceCreator = new SettingsInstanceCreator(this::scheduleSave);
	private final Gson gson;

	@Inject
	public SettingsProvider() {
		GsonBuilder gsonBuilder = new GsonBuilder() //
				.setPrettyPrinting().setLenient().disableHtmlEscaping().excludeFieldsWithoutExposeAnnotation();
		gsonBuilder.registerTypeAdapter(Settings.class, settingsInstanceCreator);
		this.gson = gsonBuilder.create();
	}

	private Path getSettingsPath() {
		final String settingsPathProperty = System.getProperty("cryptomator.settingsPath");
		return Optional.ofNullable(settingsPathProperty).filter(StringUtils::isNotBlank).map(this::replaceHomeDir).map(FileSystems.getDefault()::getPath).orElse(DEFAULT_SETTINGS_PATH);
	}

	private String replaceHomeDir(String path) {
		if (path.startsWith("~/")) {
			return SystemUtils.USER_HOME + path.substring(1);
		} else {
			return path;
		}
	}

	@Override
	public Settings get() {
		return LazyInitializer.initializeLazily(settings, this::load);
	}

	private Settings load() {
		Settings settings;
		final Path settingsPath = getSettingsPath();
		try (InputStream in = Files.newInputStream(settingsPath, StandardOpenOption.READ); //
				Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
			settings = gson.fromJson(reader, Settings.class);
			LOG.info("Settings loaded from " + settingsPath);
		} catch (IOException e) {
			LOG.info("Failed to load settings, creating new one.");
			settings = settingsInstanceCreator.createInstance(Settings.class);
		}
		return settings;
	}

	private void scheduleSave(Settings settings) {
		if (settings == null) {
			return;
		}
		ScheduledFuture<?> saveCmd = saveScheduler.schedule(() -> {
			this.save(settings);
		}, SAVE_DELAY_MS, TimeUnit.MILLISECONDS);
		ScheduledFuture<?> previousSaveCmd = scheduledSaveCmd.getAndSet(saveCmd);
		if (previousSaveCmd != null) {
			previousSaveCmd.cancel(false);
		}
	}

	private void save(Settings settings) {
		Objects.requireNonNull(settings);
		final Path settingsPath = getSettingsPath();
		try {
			Files.createDirectories(settingsPath.getParent());
			try (OutputStream out = Files.newOutputStream(settingsPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING); //
					Writer writer = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
				gson.toJson(settings, writer);
				LOG.info("Settings saved to " + settingsPath);
			}
		} catch (IOException e) {
			LOG.error("Failed to save settings.", e);
		}
	}

}
