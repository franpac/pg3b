
package com.esotericsoftware.controller.ui.swing;

import static com.esotericsoftware.minlog.Log.*;

import java.awt.AWTEvent;
import java.awt.Color;
import java.awt.Component;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowEvent;
import java.awt.event.WindowFocusListener;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
import javax.swing.UIManager;
import javax.swing.UIManager.LookAndFeelInfo;

import pnuts.lang.Package;

import com.esotericsoftware.controller.device.Axis;
import com.esotericsoftware.controller.device.Button;
import com.esotericsoftware.controller.device.Deadzone;
import com.esotericsoftware.controller.device.Device;
import com.esotericsoftware.controller.device.Stick;
import com.esotericsoftware.controller.device.Target;
import com.esotericsoftware.controller.input.Input;
import com.esotericsoftware.controller.input.Keyboard;
import com.esotericsoftware.controller.input.Mouse;
import com.esotericsoftware.controller.input.XboxController;
import com.esotericsoftware.controller.input.XboxController.Listener;
import com.esotericsoftware.controller.pg3b.ControllerType;
import com.esotericsoftware.controller.pg3b.PG3B;
import com.esotericsoftware.controller.pg3b.PG3BConfig;
import com.esotericsoftware.controller.ui.Config;
import com.esotericsoftware.controller.ui.Diagnostics;
import com.esotericsoftware.controller.ui.InputTrigger;
import com.esotericsoftware.controller.ui.Settings;
import com.esotericsoftware.controller.ui.TextMode;
import com.esotericsoftware.controller.ui.Trigger;
import com.esotericsoftware.controller.util.LoaderDialog;
import com.esotericsoftware.controller.util.Util;
import com.esotericsoftware.controller.util.WindowsRegistry;
import com.esotericsoftware.controller.xim.XIM1;
import com.esotericsoftware.controller.xim.XIM2;
import com.esotericsoftware.minlog.Log;

// BOZO - Duplicate config menu items.
// BOZO - Record using target names.
// BOZO - Customize config deactivate button.
// BOZO - Make text mode exit button same as enter trigger.

public class UI extends JFrame {
	static public final String version = "0.1.27";
	static public UI instance;

	static private Settings settings = Settings.get();

	private Device device;
	private XboxController controller;

	private JMenu pg3bMenu, xim2Menu;
	private JMenuBar menuBar;
	private JMenuItem pg3bConnectMenuItem, xim1ConnectMenuItem, xim2ConnectMenuItem, disconnectDeviceMenuItem,
		disconnectControllerMenuItem, controllerConnectMenuItem, exitMenuItem;
	private JCheckBoxMenuItem showControllerMenuItem, showLogMenuItem, pg3bDebugEnabledMenuItem, pg3bCalibrationEnabledMenuItem,
		activationDisablesInputMenuItem, xim2ThumbsticksEnabledMenuItem;
	private JMenuItem roundTripMenuItem, clearMenuItem, resetMenuItem, pg3bCalibrateMenuItem, pg3bSetControllerTypeMenuItem;

	private XboxControllerPanel controllerPanel;
	private StatusBar statusBar;

	private JTabbedPane tabs;
	private ConfigTab configTab;
	private ScriptEditor scriptEditor;
	private LogPanel logPanel;
	private JSplitPane splitPane;

	private boolean disableKeyboard = false;

	private Listener controllerListener = new Listener() {
		public void disconnected () {
			setController(null);
		}
	};

	public UI () {
		super("Controller v" + version);

		Log.set(settings.logLevel);

		if (instance != null) throw new IllegalStateException();
		instance = this;

		Package pkg = Package.getGlobalPackage();
		pkg.set("ui".intern(), this);
		pkg.set("device".intern(), null);
		pkg.set("controller".intern(), null);
		pkg.set("Axis".intern(), Axis.class);
		pkg.set("Button".intern(), Button.class);
		pkg.set("ControllerType".intern(), ControllerType.class);
		pkg.set("Deadzone".intern(), Deadzone.class);
		pkg.set("Device".intern(), PG3B.class);
		pkg.set("Stick".intern(), Stick.class);
		pkg.set("TextMode".intern(), TextMode.class);

		initializeLayout();
		initializeEvents();

		controllerPanel.setDevice(null);
		statusBar.setDevice(null);
		scriptEditor.setDevice(null);
		controllerPanel.setController(null);
		statusBar.setController(null);

		roundTripMenuItem.setEnabled(false);
		resetMenuItem.setEnabled(false);
		disconnectDeviceMenuItem.setEnabled(false);
		disconnectControllerMenuItem.setEnabled(false);
		clearMenuItem.setEnabled(false);

		if (!System.getProperty("os.name").toLowerCase().contains("windows")) {
			xim1ConnectMenuItem.setEnabled(false);
			xim2ConnectMenuItem.setEnabled(false);
		}

		controllerPanel.setVisible(settings.showController);
		showControllerMenuItem.setSelected(settings.showController);
		showLog(settings.showLog);
		showLogMenuItem.setSelected(settings.showLog);
		activationDisablesInputMenuItem.setSelected(settings.activationDisablesInput);

		statusBar.setMessage("");

		new Thread("InitialConnect") {
			public void run () {
				EventQueue.invokeLater(new Runnable() {
					public void run () {
						boolean reconnectPg3b = settings.pg3bPort != null && settings.pg3bPort.length() > 0;
						if (reconnectPg3b) new LoaderDialog("Connecting to PG3B") {
							public void load () throws Exception {
								setMessage("Opening PG3B...");
								try {
									setDevice(new PG3B(settings.pg3bPort));
								} catch (IOException ex) {
									setDevice(null);
									if (DEBUG) debug("Unable to reconnect to PG3B.", ex);
								}
							}
						}.start("Pg3bConnect");

						if (settings.xim1Connected) new LoaderDialog("Connecting to XIM1") {
							public void load () throws Exception {
								setMessage("Opening XIM1...");
								try {
									setDevice(new XIM1());
								} catch (Throwable ex) {
									setDevice(null);
									if (DEBUG) debug("Unable to reconnect to XIM1.", ex);
								}
							}
						}.start("Xim1Connect");

						if (settings.xim2Connected) new LoaderDialog("Connecting to XIM2") {
							public void load () throws Exception {
								setMessage("Opening XIM2...");
								try {
									setDevice(new XIM2());
								} catch (Throwable ex) {
									setDevice(null);
									if (DEBUG) debug("Unable to reconnect to XIM2.", ex);
								}
							}
						}.start("Xim2Connect");
					}
				});

				boolean reconnectController = settings.controllerName != null && settings.controllerName.length() > 0;
				if (reconnectController) {
					for (XboxController controller : XboxController.getAll()) {
						if (settings.controllerPort == controller.getPort() && settings.controllerName.equals(controller.getName())) {
							setController(controller);
							break;
						}
					}
				}
			}
		}.start();

		setSize(settings.windowSize.width, settings.windowSize.height);
		if (settings.windowSize.x == -1 || settings.windowSize.y == -1)
			setLocationRelativeTo(null);
		else
			setLocation(settings.windowSize.x, settings.windowSize.y);
		setExtendedState(settings.windowState);

		setVisible(true);

		int range = splitPane.getMaximumDividerLocation() - splitPane.getMinimumDividerLocation() - splitPane.getDividerSize();
		splitPane.setDividerLocation(splitPane.getMinimumDividerLocation() + (int)(range * settings.dividerLocation));
	}

	public void setDevice (Device newDevice) {
		if (device != null) device.close();

		device = newDevice;
		Package.getGlobalPackage().set("device".intern(), device);

		if (device instanceof PG3B) {
			PG3B pg3b = (PG3B)device;
			try {
				pg3b.setDebugEnabled(pg3bDebugEnabledMenuItem.isSelected());
				pg3b.setCalibrationEnabled(pg3bCalibrationEnabledMenuItem.isSelected());
			} catch (IOException ex) {
				if (Log.ERROR) error("Error setting PG3B settings.", ex);
			}
		} else if (device instanceof XIM2) {
			XIM2 xim2 = (XIM2)device;
			try {
				xim2.setThumsticksEnabled(xim2ThumbsticksEnabledMenuItem.isSelected());
			} catch (IOException ex) {
				if (Log.ERROR) error("Error setting XIM2 settings.", ex);
			}
		}

		EventQueue.invokeLater(new Runnable() {
			public void run () {
				controllerPanel.setDevice(device);
				statusBar.setDevice(device);
				scriptEditor.setDevice(device);
				configTab.getConfigEditor().setDevice(device);

				disconnectDeviceMenuItem.setEnabled(device != null);
				resetMenuItem.setEnabled(device != null);
				roundTripMenuItem.setEnabled(device != null && controller != null);

				pg3bCalibrateMenuItem.setEnabled(controller != null);

				menuBar.remove(pg3bMenu);
				menuBar.remove(xim2Menu);
				if (device instanceof PG3B) menuBar.add(pg3bMenu);
				if (device instanceof XIM2) menuBar.add(xim2Menu);
				menuBar.repaint();
			}
		});

		settings.pg3bPort = device instanceof PG3B ? ((PG3B)device).getPort() : null;
		settings.xim1Connected = device instanceof XIM1;
		settings.xim2Connected = device instanceof XIM2;
		Settings.save();
	}

	public Device getDevice () {
		return device;
	}

	public void setController (XboxController newController) {
		if (controller != null) controller.removeListener(controllerListener);
		controller = newController;
		Package.getGlobalPackage().set("controller".intern(), controller);
		if (controller != null) controller.addListener(controllerListener);

		EventQueue.invokeLater(new Runnable() {
			public void run () {
				controllerPanel.setController(controller);
				statusBar.setController(controller);

				disconnectControllerMenuItem.setEnabled(controller != null);
				roundTripMenuItem.setEnabled(controller != null && device != null);
				pg3bCalibrateMenuItem.setEnabled(roundTripMenuItem.isEnabled());
			}
		});

		settings.controllerName = controller == null ? null : controller.getName();
		settings.controllerPort = controller == null ? 0 : controller.getPort();
		Settings.save();
	}

	public XboxController getController () {
		return controller;
	}

	public XboxControllerPanel getControllerPanel () {
		return controllerPanel;
	}

	public ConfigTab getConfigTab () {
		return configTab;
	}

	public ScriptEditor getScriptEditor () {
		return scriptEditor;
	}

	public StatusBar getStatusBar () {
		return statusBar;
	}

	public JTabbedPane getTabs () {
		return tabs;
	}

	private void initializeEvents () {
		disconnectDeviceMenuItem.addActionListener(new ActionListener() {
			public void actionPerformed (ActionEvent event) {
				setDevice(null);
			}
		});

		disconnectControllerMenuItem.addActionListener(new ActionListener() {
			public void actionPerformed (ActionEvent event) {
				setController(null);
			}
		});

		xim2ConnectMenuItem.addActionListener(new ActionListener() {
			public void actionPerformed (ActionEvent event) {
				setDevice(null);
				if (!XIM2.isValid(true)) {
					Util.errorDialog(UI.this, "XIM2 Connect Error", "The XIM2 software is not installed.");
					statusBar.setMessage("XIM2 connection failed.");
					return;
				}
				new LoaderDialog("Connecting to XIM2") {
					public void load () throws Exception {
						setMessage("Opening XIM2...");
						try {
							setDevice(new XIM2());
							statusBar.setMessage("XIM2 connected.");
						} catch (Throwable ex) {
							if (ex instanceof IOException && ex.getMessage().contains("NEEDS_CALIBRATION")) {
								final String ximPath = WindowsRegistry.get("HKCU/Software/XIM", "");
								if (ximPath != null) {
									if (!new File(ximPath + "\\XIMCalibrate.exe").exists()) {
										Util.errorDialog(UI.this, "XIM2 Connect Error", "XIMCalibrate.exe could not be found.");
										return;
									}
									if (DEBUG) debug("Running XIM2 calibration tool.", ex);
									setVisible(false);
									try {
										Runtime.getRuntime().exec(ximPath + "\\XIMCalibrate.exe").waitFor();
										if (DEBUG) debug("XIM2 calibration complete.", ex);
										setDevice(new XIM2());
										statusBar.setMessage("XIM2 connected.");
										return;
									} catch (Exception ex2) {
										ex = ex2;
									} finally {
										UI.this.setVisible(true);
									}
								}
							}
							if (Log.ERROR) error("Error connecting to XIM2.", ex);
							final Throwable exception = ex;
							EventQueue.invokeLater(new Runnable() {
								public void run () {
									statusBar.setMessage("XIM2 connection failed.");
									if (exception instanceof IOException && exception.getMessage().contains("DEVICE_NOT_FOUND"))
										Util.errorDialog(UI.this, "XIM2 Connect Error", "The XIM2 device could not be found.");
									else
										Util.errorDialog(UI.this, "XIM2 Connect Error",
											"An error occurred while attempting to connect to the XIM2.");
								}
							});
						}
					}
				}.start("XIM2Connect");
			}
		});

		xim1ConnectMenuItem.addActionListener(new ActionListener() {
			public void actionPerformed (ActionEvent event) {
				setDevice(null);
				if (!XIM1.isValid(true)) {
					Util.errorDialog(UI.this, "XIM1 Connect Error", "The XIM1 software is not installed.");
					statusBar.setMessage("XIM1 connection failed.");
					return;
				}
				new LoaderDialog("Connecting to XIM1") {
					public void load () throws Exception {
						setMessage("Opening XIM1...");
						try {
							setDevice(new XIM1());
							statusBar.setMessage("XIM1 connected.");
						} catch (final Throwable ex) {
							if (Log.ERROR) error("Error connecting to XIM1.", ex);
							EventQueue.invokeLater(new Runnable() {
								public void run () {
									statusBar.setMessage("XIM1 connection failed.");
									if (ex instanceof IOException && ex.getMessage().contains("DEVICE_NOT_FOUND"))
										Util.errorDialog(UI.this, "XIM1 Connect Error", "The XIM1 device could not be found.");
									else
										Util.errorDialog(UI.this, "XIM1 Connect Error",
											"An error occurred while attempting to connect to the XIM1.");
								}
							});
						}
					}
				}.start("XIM1Connect");

			}
		});

		pg3bConnectMenuItem.addActionListener(new ActionListener() {
			public void actionPerformed (ActionEvent event) {
				setDevice(null);
				new ConnectPG3BDialog(UI.this).setVisible(true);
			}
		});

		statusBar.setDeviceClickedListener(new Runnable() {
			public void run () {
				pg3bConnectMenuItem.doClick();
			}
		});

		controllerConnectMenuItem.addActionListener(new ActionListener() {
			public void actionPerformed (ActionEvent event) {
				setController(null);
				new ConnectControllerDialog(UI.this).setVisible(true);
			}
		});
		statusBar.setControllerClickedListener(new Runnable() {
			public void run () {
				controllerConnectMenuItem.doClick();
			}
		});

		showControllerMenuItem.addActionListener(new ActionListener() {
			public void actionPerformed (ActionEvent event) {
				controllerPanel.setVisible(showControllerMenuItem.isSelected());
				settings.showController = showControllerMenuItem.isSelected();
				Settings.save();
			}
		});

		showLogMenuItem.addActionListener(new ActionListener() {
			public void actionPerformed (ActionEvent event) {
				showLog(showLogMenuItem.isSelected());
				settings.showLog = showLogMenuItem.isSelected();
				Settings.save();
			}
		});

		activationDisablesInputMenuItem.addActionListener(new ActionListener() {
			public void actionPerformed (ActionEvent event) {
				settings.activationDisablesInput = activationDisablesInputMenuItem.isSelected();
				Settings.save();
			}
		});

		exitMenuItem.addActionListener(new ActionListener() {
			public void actionPerformed (ActionEvent event) {
				exit();
			}
		});

		statusBar.setConfigClickedListener(new Runnable() {
			public void run () {
				configTab.getConfigEditor().getActivateButton().doClick();
			}
		});

		Toolkit.getDefaultToolkit().addAWTEventListener(new AWTEventListener() {
			public void eventDispatched (AWTEvent event) {
				// If mouse pressed when a component in an EditorPanel is focused, save the editor.
				if (event.getID() != MouseEvent.MOUSE_PRESSED) return;
				Component focused = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
				EditorPanel editorPanel = (EditorPanel)SwingUtilities.getAncestorOfClass(EditorPanel.class, focused);
				if (editorPanel != null && event.getSource() != focused) editorPanel.saveItem(false);
			}
		}, AWTEvent.MOUSE_EVENT_MASK);

		KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(new KeyEventDispatcher() {
			public boolean dispatchKeyEvent (KeyEvent event) {
				Config config = Config.getActive();
				if (config != null) {
					if (event.isControlDown() && event.getKeyCode() == KeyEvent.VK_F4) {
						config.setActive(false);
						if (device != null) {
							try {
								device.reset();
							} catch (IOException ex) {
								if (Log.ERROR) error("Error resetting device.", ex);
							}
						}
					}
				}
				if (disableKeyboard) event.consume();
				return false;
			}
		});

		roundTripMenuItem.addActionListener(new ActionListener() {
			public void actionPerformed (ActionEvent event) {
				new LoaderDialog("Round Trip") {
					private Map<Target, Boolean> status;

					public void load () throws Exception {
						controllerPanel.setStatus(null);
						status = Diagnostics.roundTrip(device, controller, this);
						controllerPanel.setStatus(status);
						clearMenuItem.setEnabled(true);
					}

					public void complete () {
						if (status.values().contains(Boolean.FALSE))
							statusBar.setMessage("Round trip unsuccessful.");
						else
							statusBar.setMessage("Round trip successful.");
					}
				}.start("RoundTripTest");
			}
		});

		clearMenuItem.addActionListener(new ActionListener() {
			public void actionPerformed (ActionEvent event) {
				controllerPanel.setStatus(null);
				clearMenuItem.setEnabled(false);
			}
		});

		resetMenuItem.addActionListener(new ActionListener() {
			public void actionPerformed (ActionEvent event) {
				try {
					device.reset();
				} catch (IOException ex) {
					if (Log.ERROR) error("Error resetting device.", ex);
				}
			}
		});

		pg3bCalibrateMenuItem.addActionListener(new ActionListener() {
			public void actionPerformed (ActionEvent event) {
				new PG3BCalibrationDialog(UI.this, (PG3B)device, controller);
			}
		});

		pg3bSetControllerTypeMenuItem.addActionListener(new ActionListener() {
			public void actionPerformed (ActionEvent event) {
				PG3BConfig config = ((PG3B)device).getConfig();
				int result = JOptionPane.showOptionDialog(UI.this, "Select the type of controller to which the PG3B is wired:",
					"PG3B Controller Type", JOptionPane.OK_OPTION, JOptionPane.PLAIN_MESSAGE, null, ControllerType.values(), config
						.getControllerType());
				if (result == JOptionPane.CLOSED_OPTION) return;
				try {
					config.setControllerType(ControllerType.values()[result]);
					config.save();
					device.reset();
				} catch (IOException ex) {
					if (Log.ERROR) error("Error setting PG3B controller type.", ex);
				}
			}
		});

		pg3bDebugEnabledMenuItem.addActionListener(new ActionListener() {
			public void actionPerformed (ActionEvent event) {
				try {
					((PG3B)device).setDebugEnabled(pg3bDebugEnabledMenuItem.isSelected());
				} catch (IOException ex) {
					if (Log.ERROR) error("Error setting PG3B debug.", ex);
				}
			}
		});

		pg3bCalibrationEnabledMenuItem.addActionListener(new ActionListener() {
			public void actionPerformed (ActionEvent event) {
				try {
					((PG3B)device).setCalibrationEnabled(pg3bCalibrationEnabledMenuItem.isSelected());
					device.reset();
				} catch (IOException ex) {
					if (Log.ERROR) error("Error setting PG3B calibration.", ex);
				}
			}
		});

		xim2ThumbsticksEnabledMenuItem.addActionListener(new ActionListener() {
			public void actionPerformed (ActionEvent event) {
				try {
					((XIM2)device).setThumsticksEnabled(xim2ThumbsticksEnabledMenuItem.isSelected());
				} catch (IOException ex) {
					if (Log.ERROR) error("Error setting XIM2 thumbsticks.", ex);
				}
			}
		});

		addWindowFocusListener(new WindowFocusListener() {
			public void windowLostFocus (WindowEvent event) {
				// Prevent buttons from being stuck down when the app loses focus.
				Mouse.instance.reset();
				Keyboard.instance.reset();
			}

			public void windowGainedFocus (WindowEvent event) {
			}
		});

		addComponentListener(new ComponentAdapter() {
			public void componentResized (ComponentEvent event) {
				saveFrameState();
			}
		});

		splitPane.addPropertyChangeListener(new PropertyChangeListener() {
			public void propertyChange (PropertyChangeEvent event) {
				if (!event.getPropertyName().equals(JSplitPane.DIVIDER_LOCATION_PROPERTY)) return;
				saveFrameState();
			}
		});
	}

	public void updateActiveConfig () {
		Config config = Config.getActive();
		configTab.getConfigEditor().getActivateButton().setSelected(config != null);
		statusBar.setConfig(config);

		if (config == null) {
			getGlassPane().setVisible(false);
			disableKeyboard = false;
			Mouse.instance.release();
		} else {
			if (settings.activationDisablesInput) {
				// Disable mouse and/or keyboard.
				for (Trigger trigger : config.getTriggers()) {
					if (trigger instanceof InputTrigger) {
						Input input = ((InputTrigger)trigger).getInput();
						if (input instanceof Mouse.MouseInput || input instanceof Keyboard.KeyboardInput) {
							getGlassPane().setVisible(true);
							Mouse.instance.grab(UI.this);
							disableKeyboard = true;
							break;
						}
					}
				}
			}
		}

		configTab.getConfigEditor().setSelectedItem(config);
	}

	protected void processWindowEvent (WindowEvent event) {
		if (event.getID() == WindowEvent.WINDOW_CLOSING) {
			exit();
		}
		super.processWindowEvent(event);
	}

	void exit () {
		if (device != null) device.close();
		dispose();
		System.exit(0);
	}

	private void showLog (boolean showLog) {
		getContentPane().remove(splitPane);
		getContentPane().remove(tabs);
		Component component;
		if (showLog) {
			component = splitPane;
			splitPane.setTopComponent(tabs);
		} else {
			component = tabs;
		}
		getContentPane().add(
			component,
			new GridBagConstraints(0, 2, 1, 1, 1.0, 1.0, GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(0, 0, 0, 0),
				0, 0));
		getContentPane().validate();
		splitPane.setDividerLocation(settings.dividerLocation);
	}

	private void saveFrameState () {
		if (!isVisible()) return;

		int extendedState = getExtendedState();
		if (extendedState == JFrame.ICONIFIED) extendedState = JFrame.NORMAL;

		Rectangle windowSize = settings.windowSize;
		if ((extendedState & JFrame.MAXIMIZED_BOTH) == 0) windowSize = getBounds();

		float dividerLocation = settings.dividerLocation;
		if (splitPane.getParent() != null) {
			dividerLocation = (splitPane.getDividerLocation() - splitPane.getMinimumDividerLocation())
				/ (float)(splitPane.getMaximumDividerLocation() - splitPane.getMinimumDividerLocation());
			if (dividerLocation < 0 || dividerLocation > 1) dividerLocation = 0.66f;
		}

		if (settings.windowState == extendedState && settings.windowSize.equals(windowSize)
			&& settings.dividerLocation == dividerLocation) return;
		settings.windowState = extendedState;
		settings.windowSize = windowSize;
		settings.dividerLocation = dividerLocation;
		Settings.save();
	}

	private void initializeLayout () {
		UIManager.put("FileChooser.readOnly", Boolean.TRUE);
		ToolTipManager.sharedInstance().setDismissDelay(Integer.MAX_VALUE);

		for (LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
			if ("Nimbus".equals(info.getName())) {
				try {
					UIManager.setLookAndFeel(info.getClassName());
				} catch (Throwable ignored) {
				}
				break;
			}
		}

		setIconImage(new ImageIcon(getClass().getResource("/app.png")).getImage());
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);

		{
			menuBar = new JMenuBar();
			setJMenuBar(menuBar);
			{
				JMenu menu = new JMenu("Device");
				menu.setMnemonic('D');
				menuBar.add(menu);
				{
					pg3bConnectMenuItem = menu.add(new JMenuItem("Connect to PG3B..."));
					xim1ConnectMenuItem = menu.add(new JMenuItem("Connect to XIM1..."));
					xim2ConnectMenuItem = menu.add(new JMenuItem("Connect to XIM2..."));
					resetMenuItem = menu.add(new JMenuItem("Reset Device"));
					disconnectDeviceMenuItem = menu.add(new JMenuItem("Disconnect Device"));
				}
				menu.addSeparator();
				{
					controllerConnectMenuItem = menu.add(new JMenuItem("Connect to Controller..."));
					disconnectControllerMenuItem = menu.add(new JMenuItem("Disconnect Controller"));
				}
				menu.addSeparator();
				{
					exitMenuItem = new JMenuItem("Exit");
					menu.add(exitMenuItem);
				}
			}
			{
				JMenu menu = new JMenu("View");
				menu.setMnemonic('V');
				menuBar.add(menu);
				{
					showControllerMenuItem = new JCheckBoxMenuItem("Show Controller");
					menu.add(showControllerMenuItem);
				}
				{
					showLogMenuItem = new JCheckBoxMenuItem("Show Log");
					menu.add(showLogMenuItem);
				}
				menu.addSeparator();
				{
					activationDisablesInputMenuItem = new JCheckBoxMenuItem("Activation Disables Input");
					menu.add(activationDisablesInputMenuItem);
				}
			}
			{
				JMenu menu = new JMenu("Diagnostics");
				menu.setMnemonic('i');
				menuBar.add(menu);
				{
					roundTripMenuItem = new JMenuItem("Round Trip...");
					menu.add(roundTripMenuItem);
				}
				{
					clearMenuItem = new JMenuItem("Clear");
					menu.add(clearMenuItem);
				}
			}
			{
				pg3bMenu = new JMenu("PG3B");
				pg3bMenu.setMnemonic('P');
				{
					pg3bSetControllerTypeMenuItem = new JMenuItem("Controller Type...");
					pg3bMenu.add(pg3bSetControllerTypeMenuItem);
				}
				{
					pg3bCalibrateMenuItem = new JMenuItem("Axes Calibration...");
					pg3bMenu.add(pg3bCalibrateMenuItem);
				}
				pg3bMenu.addSeparator();
				{
					pg3bDebugEnabledMenuItem = new JCheckBoxMenuItem("Debug");
					pg3bMenu.add(pg3bDebugEnabledMenuItem);
				}
				{
					pg3bCalibrationEnabledMenuItem = new JCheckBoxMenuItem("Calibration");
					pg3bMenu.add(pg3bCalibrationEnabledMenuItem);
					pg3bCalibrationEnabledMenuItem.setSelected(true);
				}
			}
			{
				xim2Menu = new JMenu("XIM2");
				xim2Menu.setMnemonic('X');
				{
					xim2ThumbsticksEnabledMenuItem = new JCheckBoxMenuItem("Thumbsticks Enabled");
					xim2Menu.add(xim2ThumbsticksEnabledMenuItem);
				}
			}
		}

		getContentPane().setLayout(new GridBagLayout());
		{
			controllerPanel = new XboxControllerPanel();
			getContentPane().add(
				controllerPanel,
				new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.CENTER, GridBagConstraints.CENTER, new Insets(0, 0,
					0, 0), 0, 0));
		}
		{
			statusBar = new StatusBar();
			getContentPane().add(
				statusBar,
				new GridBagConstraints(0, 3, 1, 1, 1.0, 0.0, GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(0, 0, 0,
					0), 0, 0));
		}
		{
			splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
			splitPane.setBorder(BorderFactory.createEmptyBorder());
			{
				splitPane.setTopComponent(tabs = new JTabbedPane());
				{
					configTab = new ConfigTab(this);
					tabs.addTab("Configuration", null, configTab, null);
					scriptEditor = new ScriptEditor(this);
					tabs.addTab("Scripts", null, scriptEditor, null);
				}
			}
			{
				splitPane.setBottomComponent(logPanel = new LogPanel());
			}
		}
		{
			JPanel glassPane = new JPanel(new GridBagLayout()) {
				{
					enableEvents(AWTEvent.MOUSE_WHEEL_EVENT_MASK);
				}

				public void paintComponent (Graphics g) {
					g.setColor(new Color(0, 0, 0, 70));
					g.fillRect(0, 0, getWidth(), getHeight());
				}
			};
			glassPane.addMouseListener(new MouseAdapter() {
				public void mousePressed (MouseEvent event) {
					event.consume();
				}
			});
			glassPane.setOpaque(false);
			glassPane.setCursor(getToolkit().createCustomCursor(new BufferedImage(3, 3, BufferedImage.TYPE_INT_ARGB),
				new Point(0, 0), "null"));
			setGlassPane(glassPane);
			{
				JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 6));
				panel.setBorder(BorderFactory.createMatteBorder(1, 1, 1, 1, Color.black));
				glassPane.add(panel, new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.CENTER, GridBagConstraints.NONE,
					new Insets(0, 0, 0, 0), 0, 0));
				{
					JLabel label = new JLabel("Press ctrl+F4 to deactivate.");
					panel.add(label);
				}
			}
		}
	}
}
