
package com.esotericsoftware.controller.ui.swing;

import java.awt.AWTEvent;
import java.awt.Color;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.TimerTask;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;

import com.esotericsoftware.controller.device.Device;
import com.esotericsoftware.controller.input.XboxController;
import com.esotericsoftware.controller.ui.Config;
import com.esotericsoftware.controller.util.Util;

public class StatusBar extends JPanel {
	private TimerTask clearMessageTask;

	private JLabel deviceLabel, controllerLabel, configLabel, messageLabel;
	private ImageIcon greenImage, redImage;

	private XboxController lastController;

	private Device lastDevice;

	private Config lastConfig;

	public StatusBar () {
		greenImage = new ImageIcon(getClass().getResource("/green.png"));
		redImage = new ImageIcon(getClass().getResource("/red.png"));

		setLayout(new GridBagLayout());
		setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(0, 0, 0)));
		{
			deviceLabel = new JLabel("Device") {
				{
					enableEvents(AWTEvent.MOUSE_WHEEL_EVENT_MASK);
				}
			};
			add(deviceLabel, new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.CENTER, GridBagConstraints.NONE,
				new Insets(3, 6, 3, 0), 0, 0));
			deviceLabel.setIcon(redImage);
		}
		{
			controllerLabel = new JLabel("Controller") {
				{
					enableEvents(AWTEvent.MOUSE_WHEEL_EVENT_MASK);
				}
			};
			add(controllerLabel, new GridBagConstraints(1, 0, 1, 1, 0.0, 0.0, GridBagConstraints.CENTER, GridBagConstraints.NONE,
				new Insets(3, 12, 3, 0), 0, 0));
			controllerLabel.setIcon(redImage);
		}
		{
			configLabel = new JLabel("Config") {
				{
					enableEvents(AWTEvent.MOUSE_WHEEL_EVENT_MASK);
				}
			};
			add(configLabel, new GridBagConstraints(2, 0, 1, 1, 0.0, 0.0, GridBagConstraints.CENTER, GridBagConstraints.NONE,
				new Insets(3, 12, 3, 0), 0, 0));
			configLabel.setIcon(redImage);
		}
		{
			messageLabel = new JLabel();
			messageLabel.setFont(messageLabel.getFont().deriveFont(Font.BOLD));
			this.add(messageLabel, new GridBagConstraints(3, 0, 1, 1, 1.0, 0.0, GridBagConstraints.EAST, GridBagConstraints.NONE,
				new Insets(0, 0, 0, 6), 0, 0));
		}
	}

	public void setDeviceClickedListener (final Runnable listener) {
		deviceLabel.addMouseListener(new MouseAdapter() {
			public void mouseClicked (MouseEvent e) {
				listener.run();
			}
		});
	}

	public void setControllerClickedListener (final Runnable listener) {
		controllerLabel.addMouseListener(new MouseAdapter() {
			public void mouseClicked (MouseEvent e) {
				listener.run();
			}
		});
	}

	public void setConfigClickedListener (final Runnable listener) {
		configLabel.addMouseListener(new MouseAdapter() {
			public void mouseClicked (MouseEvent e) {
				listener.run();
			}
		});
	}

	public void setDevice (final Device device) {
		EventQueue.invokeLater(new Runnable() {
			public void run () {
				deviceLabel.setIcon(device == null ? redImage : greenImage);
				deviceLabel.setText(device == null ? "Device" : "Device: " + device);
				if (lastDevice != null && device == null)
					setMessage("Device disconnected.");
				else if (device != null) {
					setMessage("Device connected.");
				}
				lastDevice = device;
			}
		});
	}

	public void setController (final XboxController controller) {
		controllerLabel.setIcon(controller == null ? redImage : greenImage);
		controllerLabel.setText(controller == null ? "Controller" : "Controller: " + controller.getPort());
		if (lastController != null && controller == null)
			setMessage("Controller disconnected.");
		else if (controller != null) {
			setMessage("Controller connected.");
		}
		lastController = controller;
	}

	public void setConfig (Config config) {
		configLabel.setIcon(config == null ? redImage : greenImage);
		configLabel.setText(config == null ? "Config" : "Config: " + config.getName());
		if (lastConfig != null && config == null)
			setMessage("Config deactivated.");
		else if (config != null) {
			setMessage("Config activated.");
		}
		lastConfig = config;
	}

	public synchronized void setMessage (String message) {
		if (clearMessageTask != null) clearMessageTask.cancel();
		if (message != null && message.length() > 0) {
			clearMessageTask = new TimerTask() {
				float alpha = 1;

				public void run () {
					try {
						EventQueue.invokeAndWait(new Runnable() {
							public void run () {
								alpha -= 0.03f;
								if (alpha > 0)
									messageLabel.setForeground(new Color(0, 0, 0, alpha));
								else {
									cancel();
									clearMessageTask = null;
									setMessage("");
								}
							}
						});
					} catch (Exception ignored) {
					}
				}
			};
			Util.timer.scheduleAtFixedRate(clearMessageTask, 83, 166);
		}
		messageLabel.setText(message);
		messageLabel.setForeground(Color.black);
	}
}
