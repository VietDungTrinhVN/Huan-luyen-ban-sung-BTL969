/*
 * PhanMemBanSung - Software for Laser Dry Fire Training
 * Copyright (C) 2016 phrack
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

package com.shootoff.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeoutException;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.slf4j.LoggerFactory;

import com.shootoff.Main;
import com.shootoff.camera.CameraFactory;
import com.shootoff.camera.CameraManager;
import com.shootoff.camera.cameratypes.Camera;
import com.shootoff.camera.cameratypes.IpCamera;
import com.shootoff.camera.processors.MalfunctionsProcessor;
import com.shootoff.camera.processors.ShotProcessor;
import com.shootoff.camera.processors.VirtualMagazineProcessor;
import com.shootoff.gui.CalibrationOption;
import com.shootoff.gui.controller.VideoPlayerController;
import com.shootoff.plugins.TrainingExercise;
import com.shootoff.plugins.engine.Plugin;
import com.shootoff.session.SessionRecorder;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import javafx.geometry.Point2D;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.paint.Color;

/**
 * Used to parse, store, and update configuration data from a file and in memory
 * at run time. All of ShootOFF's global settings are managed by this class.
 * 
 * @author phrack
 */
public class Configuration {
	private static final org.slf4j.Logger logger = LoggerFactory.getLogger(Configuration.class);

	private static final String FIRST_RUN_PROP = "shootoff.firstrun";
	private static final String ERROR_REPORTING_PROP = "shootoff.errorreporting";
	private static final String IPCAMS_PROP = "shootoff.ipcams";
	private static final String WEBCAMS_PROP = "shootoff.webcams";
	private static final String RECORDING_WEBCAMS_PROP = WEBCAMS_PROP + ".recording";
	private static final String MARKER_RADIUS_PROP = "shootoff.markerradius";
	private static final String IGNORE_LASER_COLOR_PROP = "shootoff.ignorelasercolor";
	private static final String USE_RED_LASER_SOUND_PROP = "shootoff.redlasersound.use";
	private static final String RED_LASER_SOUND_PROP = "shootoff.redlasersound";
	private static final String USE_GREEN_LASER_SOUND_PROP = "shootoff.greenlasersound.use";
	private static final String GREEN_LASER_SOUND_PROP = "shootoff.greenlasersound";
	private static final String USE_VIRTUAL_MAGAZINE_PROP = "shootoff.virtualmagazine.use";
	private static final String VIRTUAL_MAGAZINE_CAPACITY_PROP = "shootoff.virtualmagazine.capacity";
	private static final String USE_MALFUNCTIONS_PROP = "shootoff.malfunctions.use";
	private static final String MALFUNCTIONS_PROBABILITY_PROP = "shootoff.malfunctions.probability";
	private static final String ARENA_POSITION_X_PROP = "shootoff.arena.x";
	private static final String ARENA_POSITION_Y_PROP = "shootoff.arena.y";
	private static final String MUTED_CHIME_MESSAGES = "shootoff.diagnosticmessages.chime.muted";
	private static final String PERSPECTIVE_WEBCAM_DISTANCES = WEBCAMS_PROP + ".distances";
	private static final String CALIBRATED_FEED_BEHAVIOR_PROP = "shootoff.arena.calibrated.behavior";
	private static final String SHOW_ARENA_SHOT_MARKERS = "shootoff.arena.show.markers";
	private static final String CALIBRATE_AUTO_ADJUST_EXPOSURE = "shootoff.arena.calibrated.exposure";
	private static final String SHOWED_PERSPECTIVE_USAGE_MESSAGE = "shootoff.arena.notified.perspective";

	private static final String POI_ADJUSTMENT_X = "shootoff.poiadjust.x";
	private static final String POI_ADJUSTMENT_Y = "shootoff.poiadjust.y";

	protected static final String MARKER_RADIUS_MESSAGE = "MARKER_RADIUS has an invalid value: %d. Acceptable values are "
			+ "between 1 and 20.";
	protected static final String LASER_COLOR_MESSAGE = "LASER_COLOR has an invalid value: %s. Acceptable values are "
			+ "\"red\" and \"green\".";
	protected static final String LASER_SOUND_MESSAGE = "LASER_SOUND has an invalid value: %s. Sound file must exist.";
	protected static final String VIRTUAL_MAGAZINE_MESSAGE = "VIRTUAL_MAGAZINE has an invalid value: %d. Acceptable values are "
			+ "between 1 and 45.";
	protected static final String INJECT_MALFUNCTIONS_MESSAGE = "INJECT_MALFUNCTIONS has an invalid value: %f. Acceptable values are "
			+ "between 0.1 and 99.9.";

	private static final String DEFAULT_CONFIG_FILE = "phanmembansung.properties";

	private static final int DEFAULT_DISPLAY_WIDTH = 640;
	private static final int DEFAULT_DISPLAY_HEIGHT = 480;

	private InputStream configInput;
	private final String configName;

	private boolean isFirstRun = false;
	private boolean useErrorReporting = true;
	private final Map<String, URL> ipcams = new HashMap<>();
	private final Map<String, String> ipcamCredentials = new HashMap<>();
	private final Map<String, Camera> webcams = new HashMap<>();
	private int markerRadius = 4;
	private boolean ignoreLaserColor = false;
	private String ignoreLaserColorName = "None";
	private boolean useRedLaserSound = false;
	private File redLaserSound = new File("sounds/walther_ppq.wav");
	private boolean useGreenLaserSound = false;
	private File greenLaserSound = new File("sounds/walther_ppq.wav");
	private boolean useVirtualMagazine = false;
	private int virtualMagazineCapacity = 7;
	private boolean useMalfunctions = false;
	private float malfunctionsProbability = (float) 10.0;
	private boolean debugMode = false;
	private boolean headless = false;
	private Set<Camera> recordingCameras = new HashSet<>();
	private final Set<CameraManager> recordingManagers = new HashSet<>();
	private final Set<VideoPlayerController> videoPlayers = new HashSet<>();
	private Optional<SessionRecorder> sessionRecorder = Optional.empty();
	private TrainingExercise currentExercise = null;
	private Plugin currentPlugin = null;
	private Optional<Color> shotRowColor = Optional.empty();
	private Optional<Point2D> arenaPosition = Optional.empty();
	private final Map<String, Integer> cameraDistances = new HashMap<>();
	private final Set<String> messagesChimeMuted = new HashSet<>();
	private boolean showedPerspectiveMessage = false;

	private int displayWidth = DEFAULT_DISPLAY_WIDTH;

	private int displayHeight = DEFAULT_DISPLAY_HEIGHT;

	private final boolean debugShotsRecordToFiles = false;

	private final Set<ShotProcessor> shotProcessors = new HashSet<>();
	private VirtualMagazineProcessor magazineProcessor = null;
	private MalfunctionsProcessor malfunctionsProcessor = null;
	private CalibrationOption calibratedFeedBehavior = CalibrationOption.ONLY_IN_BOUNDS;
	private boolean showArenaShotMarkers = false;
	private boolean autoAdjustExposure = true;

	private Optional<Double> poiAdjustmentX = Optional.empty();
	private Optional<Double> poiAdjustmentY = Optional.empty();
	private boolean adjustingPOI = false;
	private int poiAdjustmentCount = 0;

	private static Configuration config = null;

	public static Configuration getConfig() {
		return config;
	}

	private static void setConfig(Configuration config) {
		Configuration.config = config;
	}

	protected Configuration(InputStream configInputStream, String name) throws IOException, ConfigurationException {
		configInput = configInputStream;
		configName = name;
		readConfigurationFile();

		setConfig(this);
	}

	protected Configuration(String name) throws IOException, ConfigurationException {
		configName = name;
		readConfigurationFile();

		setConfig(this);
	}

	protected Configuration(InputStream configInputStream, String name, String[] args)
			throws IOException, ConfigurationException {
		configInput = configInputStream;
		configName = name;
		parseCmdLine(args);
		readConfigurationFile();
		parseCmdLine(args); // Parse twice so that we guarantee debug is set and
		// override config file

		setConfig(this);
	}

	/**
	 * Loads the configuration from a file named <tt>name</tt> and then updates
	 * the configuration using the programs arguments stored in <tt>args</tt>.
	 * 
	 * @param name
	 *            the configuration file to load properties from
	 * @param args
	 *            the command line arguments for this program
	 * @throws IOException
	 *             <tt>name</tt> doesn't exist on the file system
	 * @throws ConfigurationException
	 *             a specific property value is out of spec
	 */
	public Configuration(String name, String[] args) throws IOException, ConfigurationException {
		configName = name;
		parseCmdLine(args);
		readConfigurationFile();
		parseCmdLine(args);

		setConfig(this);
	}

	public Configuration(String[] args) throws ConfigurationException {
		configName = DEFAULT_CONFIG_FILE;
		parseCmdLine(args);

		setConfig(this);
	}

	private void readConfigurationFile() throws ConfigurationException, IOException {
		InputStream inputStream;

		if (configInput != null) {
			inputStream = configInput;
		} else {
			try {
				inputStream = new FileInputStream(configName);
			} catch (final FileNotFoundException e) {
				throw new FileNotFoundException("Could not read configuration file " + configName);
			}
		}

		final Properties prop = new Properties();

		try {
			prop.load(inputStream);
		} catch (final IOException ioe) {
			throw ioe;
		} finally {
			inputStream.close();
		}

		if (prop.containsKey(FIRST_RUN_PROP)) {
			setFirstRun(Boolean.parseBoolean(prop.getProperty(FIRST_RUN_PROP)));
		} else {
			setFirstRun(false);
		}

		if (prop.containsKey(ERROR_REPORTING_PROP)) {
			setUseErrorReporting(Boolean.parseBoolean(prop.getProperty(ERROR_REPORTING_PROP)));
		}

		if (prop.containsKey(IPCAMS_PROP)) {
			for (final String nameString : prop.getProperty(IPCAMS_PROP).split(",")) {
				final String[] names = nameString.split("\\|");
				if (names.length == 2) {
					registerIpCam(names[0], names[1], Optional.empty(), Optional.empty());
				} else if (names.length > 2) {
					registerIpCam(names[0], names[1], Optional.of(names[2]), Optional.of(names[3]));
				}
			}
		}

		if (prop.containsKey(WEBCAMS_PROP)) {
			final List<String> webcamNames = new ArrayList<>();
			final List<String> webcamInternalNames = new ArrayList<>();

			for (final String nameString : prop.getProperty(WEBCAMS_PROP).split(",")) {
				final String[] names = nameString.split(":");
				if (names.length > 1) {
					webcamNames.add(names[0].replaceAll("//`", ":"));
					webcamInternalNames.add(names[1].replaceAll("//`", ":"));
				}
			}

			for (final Camera webcam : CameraFactory.getWebcams()) {
				final int cameraIndex = webcamInternalNames.indexOf(webcam.getName());
				if (cameraIndex >= 0) webcams.put(webcamNames.get(cameraIndex), webcam);

			}
		}

		final Set<Camera> recordingCameras = new HashSet<>();
		if (prop.containsKey(RECORDING_WEBCAMS_PROP)) {
			for (final String nameString : prop.getProperty(RECORDING_WEBCAMS_PROP).split(",")) {
				for (final Camera webcam : webcams.values()) {
					if (webcam.getName().equals(nameString)) {
						recordingCameras.add(webcam);
						continue;
					}
				}
			}
		}
		setRecordingCameras(recordingCameras);

		if (prop.containsKey(MARKER_RADIUS_PROP)) {
			setMarkerRadius(Integer.parseInt(prop.getProperty(MARKER_RADIUS_PROP)));
		}

		if (prop.containsKey(IGNORE_LASER_COLOR_PROP)) {
			final String colorName = prop.getProperty(IGNORE_LASER_COLOR_PROP);

			if (!colorName.equals("None")) {
				setIgnoreLaserColor(true);
				setIgnoreLaserColorName(colorName);
			}
		}

		if (prop.containsKey(USE_RED_LASER_SOUND_PROP)) {
			setUseRedLaserSound(Boolean.parseBoolean(prop.getProperty(USE_RED_LASER_SOUND_PROP)));
		}

		if (prop.containsKey(RED_LASER_SOUND_PROP)) {
			setRedLaserSound(new File(prop.getProperty(RED_LASER_SOUND_PROP)));
		}

		if (prop.containsKey(USE_GREEN_LASER_SOUND_PROP)) {
			setUseGreenLaserSound(Boolean.parseBoolean(prop.getProperty(USE_GREEN_LASER_SOUND_PROP)));
		}

		if (prop.containsKey(GREEN_LASER_SOUND_PROP)) {
			setGreenLaserSound(new File(prop.getProperty(GREEN_LASER_SOUND_PROP)));
		}

		if (prop.containsKey(USE_VIRTUAL_MAGAZINE_PROP)) {
			setUseVirtualMagazine(Boolean.parseBoolean(prop.getProperty(USE_VIRTUAL_MAGAZINE_PROP)));
		}

		if (prop.containsKey(VIRTUAL_MAGAZINE_CAPACITY_PROP)) {
			setVirtualMagazineCapacity(Integer.parseInt(prop.getProperty(VIRTUAL_MAGAZINE_CAPACITY_PROP)));
		}

		if (prop.containsKey(USE_MALFUNCTIONS_PROP)) {
			setMalfunctions(Boolean.parseBoolean(prop.getProperty(USE_MALFUNCTIONS_PROP)));
		}

		if (prop.containsKey(MALFUNCTIONS_PROBABILITY_PROP)) {
			setMalfunctionsProbability(Float.parseFloat(prop.getProperty(MALFUNCTIONS_PROBABILITY_PROP)));
		}

		if (prop.containsKey(ARENA_POSITION_X_PROP) && prop.containsKey(ARENA_POSITION_Y_PROP)) {
			setArenaPosition(Double.parseDouble(prop.getProperty(ARENA_POSITION_X_PROP)),
					Double.parseDouble(prop.getProperty(ARENA_POSITION_Y_PROP)));
		}

		if (prop.containsKey(PERSPECTIVE_WEBCAM_DISTANCES)) {
			for (final String distanceString : prop.getProperty(PERSPECTIVE_WEBCAM_DISTANCES).split(",")) {
				final String[] distanceComponents = distanceString.split("\\|");
				if (distanceComponents.length == 2) {
					cameraDistances.put(distanceComponents[0], Integer.parseInt(distanceComponents[1]));
				}
			}
		}

		if (prop.containsKey(MUTED_CHIME_MESSAGES)) {
			for (final String message : prop.getProperty(MUTED_CHIME_MESSAGES).split("\\|")) {
				muteMessageChime(message);
			}
		}

		if (prop.containsKey(CALIBRATED_FEED_BEHAVIOR_PROP)) {
			setCalibratedFeedBehavior(CalibrationOption.valueOf(prop.getProperty(CALIBRATED_FEED_BEHAVIOR_PROP)));
		}

		if (prop.containsKey(SHOW_ARENA_SHOT_MARKERS)) {
			setShowArenaShotMarkers(Boolean.parseBoolean(prop.getProperty(SHOW_ARENA_SHOT_MARKERS)));
		}
		
		if (prop.containsKey(SHOWED_PERSPECTIVE_USAGE_MESSAGE)) {
			setShowedPerspectiveMessage(Boolean.parseBoolean(prop.getProperty(SHOWED_PERSPECTIVE_USAGE_MESSAGE)));
		}

		if (prop.containsKey(CALIBRATE_AUTO_ADJUST_EXPOSURE)) {
			setAutoAdjustExposure(Boolean.parseBoolean(prop.getProperty(CALIBRATE_AUTO_ADJUST_EXPOSURE)));
		}

		if (prop.containsKey(POI_ADJUSTMENT_X) && prop.containsKey(POI_ADJUSTMENT_Y)) {
			poiAdjustmentX = Optional.of(Double.parseDouble(prop.getProperty(POI_ADJUSTMENT_X)));
			poiAdjustmentY = Optional.of(Double.parseDouble(prop.getProperty(POI_ADJUSTMENT_Y)));
			adjustingPOI = true;
			logger.info("POI Adjustment loaded from config, x {} y {}", poiAdjustmentX.get(), poiAdjustmentY.get());
		}

		validateConfiguration();
	}

	public boolean writeConfigurationFile() throws ConfigurationException, IOException {
		validateConfiguration();

		if (!new File(configName).canWrite()) {
			final Alert writeAlert = new Alert(AlertType.ERROR);
			writeAlert.setTitle("Cannot Persist Preferences");
			writeAlert.setHeaderText("Configuration File Unwritable!");
			writeAlert.setResizable(true);
			writeAlert.setContentText("The file " + configName + " is not writable, thus your preferences"
					+ " cannot be saved. This is likely the case because you placed PhanMemBanSung in a location"
					+ " that only the administrator can write to, but PhanMemBanSung is not running as an"
					+ " administrator. Please either move PhanMemBanSung to a different location or grant write"
					+ " privileges to the file.");
			writeAlert.showAndWait();

			return false;
		}

		final Properties prop = new Properties();

		final StringBuilder ipcamList = new StringBuilder();
		for (final Entry<String, URL> entry : ipcams.entrySet()) {
			if (ipcamList.length() > 0) ipcamList.append(",");
			ipcamList.append(entry.getKey());
			ipcamList.append("|");
			ipcamList.append(entry.getValue().toString());

			if (ipcamCredentials.containsKey(entry.getKey())) {
				ipcamList.append("|");
				ipcamList.append(ipcamCredentials.get(entry.getKey()));
			}
		}

		final StringBuilder webcamList = new StringBuilder();
		for (final Entry<String, Camera> entry : webcams.entrySet()) {
			if (webcamList.length() > 0) webcamList.append(",");
			webcamList.append(entry.getKey().replaceAll(":", "//`"));
			webcamList.append(":");
			webcamList.append(entry.getValue().getName().replaceAll(":", "//`"));
		}

		final StringBuilder recordingWebcamList = new StringBuilder();
		for (final Camera c : recordingCameras) {
			if (recordingWebcamList.length() > 0) recordingWebcamList.append(",");
			recordingWebcamList.append(c.getName());
		}

		final StringBuilder mutedChimeMessages = new StringBuilder();
		for (final String m : messagesChimeMuted) {
			if (mutedChimeMessages.length() > 0) mutedChimeMessages.append("|");
			mutedChimeMessages.append(m);
		}

		final StringBuilder cameraDistancesList = new StringBuilder();
		for (final Entry<String, Integer> distanceEntry : cameraDistances.entrySet()) {
			if (cameraDistancesList.length() > 0) cameraDistancesList.append(",");
			cameraDistancesList.append(distanceEntry.getKey());
			cameraDistancesList.append("|");
			cameraDistancesList.append(distanceEntry.getValue());
		}

		prop.setProperty(FIRST_RUN_PROP, String.valueOf(isFirstRun));
		prop.setProperty(ERROR_REPORTING_PROP, String.valueOf(useErrorReporting));
		prop.setProperty(IPCAMS_PROP, ipcamList.toString());
		prop.setProperty(WEBCAMS_PROP, webcamList.toString());
		prop.setProperty(RECORDING_WEBCAMS_PROP, recordingWebcamList.toString());
		prop.setProperty(MARKER_RADIUS_PROP, String.valueOf(markerRadius));
		prop.setProperty(IGNORE_LASER_COLOR_PROP, ignoreLaserColorName);
		prop.setProperty(USE_RED_LASER_SOUND_PROP, String.valueOf(useRedLaserSound));
		prop.setProperty(RED_LASER_SOUND_PROP, redLaserSound.getPath());
		prop.setProperty(USE_GREEN_LASER_SOUND_PROP, String.valueOf(useGreenLaserSound));
		prop.setProperty(GREEN_LASER_SOUND_PROP, greenLaserSound.getPath());
		prop.setProperty(USE_VIRTUAL_MAGAZINE_PROP, String.valueOf(useVirtualMagazine));
		prop.setProperty(VIRTUAL_MAGAZINE_CAPACITY_PROP, String.valueOf(virtualMagazineCapacity));
		prop.setProperty(USE_MALFUNCTIONS_PROP, String.valueOf(useMalfunctions));
		prop.setProperty(MALFUNCTIONS_PROBABILITY_PROP, String.valueOf(malfunctionsProbability));
		prop.setProperty(MUTED_CHIME_MESSAGES, mutedChimeMessages.toString());

		if (getArenaPosition().isPresent()) {
			final Point2D arenaPosition = getArenaPosition().get();

			prop.setProperty(ARENA_POSITION_X_PROP, String.valueOf(arenaPosition.getX()));
			prop.setProperty(ARENA_POSITION_Y_PROP, String.valueOf(arenaPosition.getY()));
		}

		prop.setProperty(PERSPECTIVE_WEBCAM_DISTANCES, cameraDistancesList.toString());
		prop.setProperty(CALIBRATED_FEED_BEHAVIOR_PROP, calibratedFeedBehavior.name());
		prop.setProperty(SHOW_ARENA_SHOT_MARKERS, String.valueOf(showArenaShotMarkers));
		prop.setProperty(CALIBRATE_AUTO_ADJUST_EXPOSURE, String.valueOf(autoAdjustExposure));
		prop.setProperty(SHOWED_PERSPECTIVE_USAGE_MESSAGE, String.valueOf(showedPerspectiveMessage));

		if (isAdjustingPOI() && poiAdjustmentX.isPresent() && poiAdjustmentY.isPresent()) {
			prop.setProperty(POI_ADJUSTMENT_X, String.valueOf(poiAdjustmentX.get()));
			prop.setProperty(POI_ADJUSTMENT_Y, String.valueOf(poiAdjustmentY.get()));
		}

		final OutputStream outputStream = new FileOutputStream(configName);

		try {
			prop.store(outputStream, "PhanMemBanSung Configuration");
			outputStream.flush();
		} catch (final IOException ioe) {
			throw ioe;
		} finally {
			outputStream.close();
		}

		return true;
	}

	private void parseCmdLine(String[] args) throws ConfigurationException {
		final Options options = new Options();

		options.addOption("d", "debug", false, "turn on debug log messages");
		options.addOption("h", "headless", false, "run without the main GUI and immediately open the projector arena");
		options.addOption("m", "marker-radius", true, "sets the radius of shot markers in pixels [1,20]");
		options.addOption("c", "ignore-laser-color", true,
				"sets the color of laser that should be ignored by PhanMemBanSung (green "
						+ "or red). No color is ignored by default");
		options.addOption("u", "use-virtual-magazine", true,
				"turns on the virtual magazine and sets the number rounds it holds [1,45]");
		options.addOption("f", "use-malfunctions", true,
				"turns on malfunctions and sets the probability of them happening");

		try {
			final CommandLineParser parser = new DefaultParser();
			final CommandLine cmd = parser.parse(options, args);

			if (cmd.hasOption("d")) setDebugMode(true);

			if (cmd.hasOption("h")) headless = true;

			if (cmd.hasOption("m")) setMarkerRadius(Integer.parseInt(cmd.getOptionValue("m")));

			if (cmd.hasOption("c")) {
				setIgnoreLaserColor(true);
				setIgnoreLaserColorName(cmd.getOptionValue("c"));
			}

			if (cmd.hasOption("u")) {
				setUseVirtualMagazine(true);
				setVirtualMagazineCapacity(Integer.parseInt(cmd.getOptionValue("u")));
			}

			if (cmd.hasOption("f")) {
				setMalfunctions(true);
				setMalfunctionsProbability(Float.parseFloat(cmd.getOptionValue("f")));
			}
		} catch (final ParseException e) {
			System.err.println(e.getMessage());
			final HelpFormatter formatter = new HelpFormatter();
			formatter.printHelp("com.shootoff.Main", options);
			Main.forceClose(-1);
		}

		validateConfiguration();
	}

	protected void validateConfiguration() throws ConfigurationException {
		if (markerRadius < 1 || markerRadius > 20) {
			throw new ConfigurationException(String.format(MARKER_RADIUS_MESSAGE, markerRadius));
		}

		if (!redLaserSound.isAbsolute())
			redLaserSound = new File(System.getProperty("shootoff.home") + File.separator + redLaserSound.getPath());

		if (useRedLaserSound && !redLaserSound.exists()) {
			throw new ConfigurationException(String.format(LASER_SOUND_MESSAGE, redLaserSound.getPath()));
		}

		if (!greenLaserSound.isAbsolute()) greenLaserSound = new File(
				System.getProperty("shootoff.home") + File.separator + greenLaserSound.getPath());

		if (useGreenLaserSound && !greenLaserSound.exists()) {
			throw new ConfigurationException(String.format(LASER_SOUND_MESSAGE, greenLaserSound.getPath()));
		}

		if (ignoreLaserColor && !ignoreLaserColorName.equals("red") && !ignoreLaserColorName.equals("green")) {
			throw new ConfigurationException(String.format(LASER_COLOR_MESSAGE, ignoreLaserColorName));
		}

		if (virtualMagazineCapacity < 1 || virtualMagazineCapacity > 45) {
			throw new ConfigurationException(String.format(VIRTUAL_MAGAZINE_MESSAGE, virtualMagazineCapacity));
		}

		if (malfunctionsProbability < (float) 0.1 || malfunctionsProbability > (float) 99.9) {
			throw new ConfigurationException(String.format(INJECT_MALFUNCTIONS_MESSAGE, malfunctionsProbability));
		}
	}

	public int getDisplayWidth() {
		return displayWidth;
	}

	public int getDisplayHeight() {
		return displayHeight;
	}

	public void setDisplayResolution(int displayWidth, int displayHeight) {
		this.displayWidth = displayWidth;
		this.displayHeight = displayHeight;
	}

	public boolean isFirstRun() {
		return isFirstRun;
	}

	public void setFirstRun(boolean isFirstRun) {
		this.isFirstRun = isFirstRun;
	}

	public boolean useErrorReporting() {
		return useErrorReporting;
	}

	public void setUseErrorReporting(boolean useErrorReporting) {
		this.useErrorReporting = useErrorReporting;
	}

	public static void disableErrorReporting() {
		final Logger rootLogger = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
		final LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
		setLogConsoleAppender(rootLogger, loggerContext);
	}

	public void registerVideoPlayer(VideoPlayerController videoPlayer) {
		videoPlayers.add(videoPlayer);
	}

	public void unregisterVideoPlayer(VideoPlayerController videoPlayer) {
		videoPlayers.remove(videoPlayer);
	}

	public Set<VideoPlayerController> getVideoPlayers() {
		return videoPlayers;
	}

	public Optional<Camera> registerIpCam(String cameraName, String cameraURL, Optional<String> username,
			Optional<String> password) {
		try {
			final URL url = new URL(cameraURL);
			final Camera cam = IpCamera.registerIpCamera(cameraName, url, username, password);
			ipcams.put(cameraName, url);

			if (username.isPresent() && password.isPresent()) {
				ipcamCredentials.put(cameraName, username.get() + "|" + password.get());
			}

			return Optional.of(cam);
		} catch (MalformedURLException | URISyntaxException ue) {
			final Alert ipcamURLAlert = new Alert(AlertType.ERROR);
			ipcamURLAlert.setTitle("Malformed URL");
			ipcamURLAlert.setHeaderText("IPCam URL is Malformed!");
			ipcamURLAlert.setResizable(true);
			ipcamURLAlert.setContentText("IPCam URL is not valid: \n\n" + ue.getMessage());
			ipcamURLAlert.showAndWait();
		} catch (final UnknownHostException uhe) {
			final Alert ipcamHostAlert = new Alert(AlertType.ERROR);
			ipcamHostAlert.setTitle("Unknown Host");
			ipcamHostAlert.setHeaderText("IPCam URL Unknown!");
			ipcamHostAlert.setResizable(true);
			ipcamHostAlert.setContentText("The IPCam at " + cameraURL
					+ " cannot be resolved. Ensure the URL is correct "
					+ "and that you are either connected to the internet or on the same network as the camera.");
			ipcamHostAlert.showAndWait();
		} catch (final TimeoutException te) {
			final Alert ipcamTimeoutAlert = new Alert(AlertType.ERROR);
			ipcamTimeoutAlert.setTitle("IPCam Timeout");
			ipcamTimeoutAlert.setHeaderText("Connection to IPCam Reached Timeout!");
			ipcamTimeoutAlert.setResizable(true);
			ipcamTimeoutAlert.setContentText("Could not communicate with the IP at " + cameraURL
					+ ". Please check the following:\n\n" + "-The IPCam URL is correct\n"
					+ "-You are connected to the Internet (for external cameras)\n"
					+ "-You are connected to the same network as the camera (for local cameras)");
			ipcamTimeoutAlert.showAndWait();
		}

		return Optional.empty();
	}

	public void unregisterIpCam(String cameraName) {
		if (IpCamera.unregisterIpCamera(cameraName)) {
			ipcams.remove(cameraName);
			ipcamCredentials.remove(cameraName);
		}
	}

	public void setWebcams(List<String> webcamNames, List<Camera> configuredCameras) {
		webcams.clear();

		for (int i = 0; i < webcamNames.size(); i++) {
			webcams.put(webcamNames.get(i), configuredCameras.get(i));
		}
	}

	public void setMarkerRadius(int markRadius) {
		markerRadius = markRadius;
	}

	public void setIgnoreLaserColor(boolean ignoreLaserColor) {
		this.ignoreLaserColor = ignoreLaserColor;
	}

	public void setIgnoreLaserColorName(String ignoreLaserColorName) {
		this.ignoreLaserColorName = ignoreLaserColorName;
	}

	public void setUseRedLaserSound(Boolean useRedLaserSound) {
		this.useRedLaserSound = useRedLaserSound;
	}

	public void setRedLaserSound(File redLaserSound) {
		this.redLaserSound = redLaserSound;
	}

	public void setUseGreenLaserSound(Boolean useGreenLaserSound) {
		this.useGreenLaserSound = useGreenLaserSound;
	}

	public void setGreenLaserSound(File greenLaserSound) {
		this.greenLaserSound = greenLaserSound;
	}

	public void setUseVirtualMagazine(boolean useVirtualMagazine) {
		this.useVirtualMagazine = useVirtualMagazine;

		if (!useVirtualMagazine && magazineProcessor != null) {
			shotProcessors.remove(magazineProcessor);
			magazineProcessor = null;
		}
	}

	public void setVirtualMagazineCapacity(int virtualMagazineCapacity) {
		this.virtualMagazineCapacity = virtualMagazineCapacity;

		if (useVirtualMagazine) {
			if (magazineProcessor != null) {
				shotProcessors.remove(magazineProcessor);
			}

			magazineProcessor = new VirtualMagazineProcessor(this);
			shotProcessors.add(magazineProcessor);
		}
	}

	public void setMalfunctions(boolean injectMalfunctions) {
		useMalfunctions = injectMalfunctions;

		if (!useMalfunctions && malfunctionsProcessor != null) {
			shotProcessors.remove(malfunctionsProcessor);
			malfunctionsProcessor = null;
		}
	}

	public void setMalfunctionsProbability(float injectMalfunctionsProbability) {
		malfunctionsProbability = injectMalfunctionsProbability;

		if (useMalfunctions) {
			if (malfunctionsProcessor != null) {
				shotProcessors.remove(malfunctionsProcessor);
			}

			malfunctionsProcessor = new MalfunctionsProcessor(this);
			shotProcessors.add(malfunctionsProcessor);
		}
	}

	public void setDebugMode(boolean debugMode) {
		this.debugMode = debugMode;

		if (debugMode) {
			// Ignore first run operations if we are running in debug mode
			setFirstRun(false);
		}

		final Logger rootLogger = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);

		if (debugMode) {
			final LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
			setLogConsoleAppender(rootLogger, loggerContext);

			if (rootLogger.getLevel().equals(Level.TRACE)) {
				return;
			}

			rootLogger.setLevel(Level.DEBUG);

			// Ensure webcam-capture logger stays at info because it is quite
			// noisy and doesn't output information we care about.
			final Logger webcamCaptureLogger = loggerContext.getLogger("com.github.sarxos");
			webcamCaptureLogger.setLevel(Level.INFO);

			// Drop WebcamDiscoveryService even lower because it is extremely
			// noisy
			final Logger webcamDiscoveryLogger = loggerContext
					.getLogger("com.github.sarxos.webcam.WebcamDiscoveryService");
			webcamDiscoveryLogger.setLevel(Level.WARN);
		} else {
			rootLogger.setLevel(Level.WARN);
		}
	}

	private static void setLogConsoleAppender(Logger rootLogger, LoggerContext loggerContext) {
		final PatternLayoutEncoder ple = new PatternLayoutEncoder();

		ple.setPattern("%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n");
		ple.setContext(loggerContext);
		ple.start();
		final ConsoleAppender<ILoggingEvent> consoleAppender = new ConsoleAppender<>();
		consoleAppender.setEncoder(ple);
		consoleAppender.setContext(loggerContext);
		consoleAppender.start();

		rootLogger.detachAndStopAllAppenders();
		rootLogger.setAdditive(false);
		rootLogger.addAppender(consoleAppender);
	}

	public void setRecordingCameras(Set<Camera> recordingCameras2) {
		recordingCameras = recordingCameras2;
	}

	public void setShotTimerRowColor(Color c) {
		shotRowColor = Optional.ofNullable(c);
	}

	public void muteMessageChime(String message) {
		messagesChimeMuted.add(message);
	}

	public void unmuteMessageChime(String message) {
		messagesChimeMuted.remove(message);
	}

	public void setCalibratedFeedBehavior(CalibrationOption calibrationOption) {
		calibratedFeedBehavior = calibrationOption;
	}

	public void setShowArenaShotMarkers(boolean showMarkers) {
		showArenaShotMarkers = showMarkers;
	}

	public void setAutoAdjustExposure(boolean autoAdjust) {
		autoAdjustExposure = autoAdjust;
	}

	public Set<Camera> getRecordingCameras() {
		return recordingCameras;
	}

	public void registerRecordingCameraManager(CameraManager cm) {
		recordingManagers.add(cm);
	}

	public void unregisterRecordingCameraManager(CameraManager cm) {
		recordingManagers.remove(cm);
	}

	public void unregisterAllRecordingCameraManagers() {
		recordingManagers.clear();
	}

	public void setSessionRecorder(SessionRecorder sessionRecorder) {
		this.sessionRecorder = Optional.ofNullable(sessionRecorder);
	}

	public void setExercise(TrainingExercise exercise) {
		if (currentExercise != null) currentExercise.destroy();

		currentExercise = exercise;
	}

	public void setPlugin(Plugin plugin) {
		currentPlugin = plugin;
	}

	public void setArenaPosition(double x, double y) {
		arenaPosition = Optional.of(new Point2D(x, y));
	}

	public void setCameraDistance(String webcamName, int distance) {
		cameraDistances.put(webcamName, distance);
	}

	public void setShowedPerspectiveMessage(boolean showedPerspectiveMessage) {
		this.showedPerspectiveMessage = showedPerspectiveMessage;
	}

	public Map<String, URL> getRegistedIpCams() {
		return ipcams;
	}

	public Map<String, Camera> getWebcams() {
		return webcams;
	}

	public Optional<String> getWebcamsUserName(Camera webcam) {
		for (final Entry<String, Camera> entry : webcams.entrySet()) {
			if (entry.getValue().equals(webcam)) return Optional.of(entry.getKey());
		}

		return Optional.empty();
	}

	public int getMarkerRadius() {
		return markerRadius;
	}

	public boolean ignoreLaserColor() {
		return ignoreLaserColor;
	}

	public Optional<Color> getIgnoreLaserColor() {
		if (ignoreLaserColorName.equals("red")) {
			return Optional.of(Color.RED);
		} else if (ignoreLaserColorName.equals("green")) {
			return Optional.of(Color.GREEN);
		}

		return Optional.empty();
	}

	public String getIgnoreLaserColorName() {
		return ignoreLaserColorName;
	}

	public boolean useRedLaserSound() {
		return useRedLaserSound;
	}

	public File getRedLaserSound() {
		if (!redLaserSound.isAbsolute())
			redLaserSound = new File(System.getProperty("shootoff.home") + File.separator + redLaserSound.getPath());

		return redLaserSound;
	}

	public boolean useGreenLaserSound() {
		return useGreenLaserSound;
	}

	public File getGreenLaserSound() {
		if (!greenLaserSound.isAbsolute()) greenLaserSound = new File(
				System.getProperty("shootoff.home") + File.separator + greenLaserSound.getPath());

		return greenLaserSound;
	}

	public boolean useVirtualMagazine() {
		return useVirtualMagazine;
	}

	public int getVirtualMagazineCapacity() {
		return virtualMagazineCapacity;
	}

	public boolean useMalfunctions() {
		return useMalfunctions;
	}

	public float getMalfunctionsProbability() {
		return malfunctionsProbability;
	}

	public boolean inDebugMode() {
		return debugMode;
	}

	public boolean isHeadless() {
		return headless;
	}

	public Optional<SessionRecorder> getSessionRecorder() {
		return sessionRecorder;
	}

	public Set<CameraManager> getRecordingManagers() {
		return recordingManagers;
	}

	public Set<ShotProcessor> getShotProcessors() {
		return shotProcessors;
	}

	public Optional<TrainingExercise> getExercise() {
		return Optional.ofNullable(currentExercise);
	}

	public Optional<Plugin> getPlugin() {
		return Optional.ofNullable(currentPlugin);
	}

	public Optional<Color> getShotTimerRowColor() {
		return shotRowColor;
	}

	public boolean isDebugShotsRecordToFiles() {
		return debugShotsRecordToFiles;
	}

	public Optional<Point2D> getArenaPosition() {
		return arenaPosition;
	}

	public Optional<Integer> getCameraDistance(String cameraName) {
		return Optional.ofNullable(cameraDistances.get(cameraName));
	}

	public boolean isChimeMuted(String message) {
		return messagesChimeMuted.contains(message);
	}

	public CalibrationOption getCalibratedFeedBehavior() {
		return calibratedFeedBehavior;
	}

	public boolean showArenaShotMarkers() {
		return showArenaShotMarkers;
	}

	public boolean showedPerspectiveMessage() {
		return showedPerspectiveMessage;
	}

	public boolean autoAdjustExposure() {
		return autoAdjustExposure;
	}

	private final static int POI_NUM_TARGETS = 5;

	// Returns true IFF the current action is TURNING OFF POI Adjustment
	// Logic:
	// 1. Does not enable POI adjustment until 5 shots have been reached
	// 2. POI is average of those 5 shots
	// 3. Sixth shot turns off POI and resets shot count to 0
	public boolean updatePOIAdjustment(double offsetX, double offsetY) {
		// If it is already enabled, disable it and return. Don't process the
		// current value
		if (adjustingPOI == true) {
			adjustingPOI = false;
			poiAdjustmentX = Optional.empty();
			poiAdjustmentY = Optional.empty();
			poiAdjustmentCount = 0;

			try {
				writeConfigurationFile();
			} catch (ConfigurationException | IOException e) {
				logger.error("Failed to disable POI", e);
				return false;
			}
			return true;
		}

		// First call
		if (poiAdjustmentCount == 0) {
			poiAdjustmentX = Optional.of(-1.0 * offsetX);
			poiAdjustmentY = Optional.of(-1.0 * offsetY);
			poiAdjustmentCount = 1;
		}
		// Second through numTargets calls
		else {
			double weightedAdjX = poiAdjustmentCount * poiAdjustmentX.get();
			double weightedAdjY = poiAdjustmentCount * poiAdjustmentY.get();

			poiAdjustmentX = Optional.of((weightedAdjX - offsetX) / (double) (poiAdjustmentCount + 1));
			poiAdjustmentY = Optional.of((weightedAdjY - offsetY) / (double) (poiAdjustmentCount + 1));

			poiAdjustmentCount++;
		}

		logger.trace("POI Adjustment: x {} y {}", poiAdjustmentX.get(), poiAdjustmentY.get());

		if (poiAdjustmentCount == POI_NUM_TARGETS) {
			adjustingPOI = true;
			
			logger.info("Setting POI Adjustment: x {} y {}", poiAdjustmentX.get(), poiAdjustmentY.get());

			try {
				writeConfigurationFile();
			} catch (ConfigurationException | IOException e) {
				logger.error("Failed to write new POI", e);
				return false;
			}
		}

		return false;
	}

	public Optional<Double> getPOIAdjustmentX() {
		return poiAdjustmentX;
	}

	public Optional<Double> getPOIAdjustmentY() {
		return poiAdjustmentY;
	}

	public boolean isAdjustingPOI() {
		return adjustingPOI;
	}
}
