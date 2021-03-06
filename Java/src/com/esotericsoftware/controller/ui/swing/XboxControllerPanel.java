
package com.esotericsoftware.controller.ui.swing;

import java.awt.AWTEvent;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Map.Entry;

import javax.imageio.ImageIO;
import javax.swing.JPanel;

import com.esotericsoftware.controller.device.Axis;
import com.esotericsoftware.controller.device.Button;
import com.esotericsoftware.controller.device.Device;
import com.esotericsoftware.controller.device.Stick;
import com.esotericsoftware.controller.device.Target;
import com.esotericsoftware.controller.input.JInputXboxController;
import com.esotericsoftware.controller.input.Mouse;
import com.esotericsoftware.controller.input.XboxController;
import com.esotericsoftware.controller.util.Listeners;
import com.esotericsoftware.controller.util.PackedImages;
import com.esotericsoftware.controller.util.Sound;
import com.esotericsoftware.controller.util.PackedImages.PackedImage;

public class XboxControllerPanel extends JPanel {
	static public final String[] imageNames = {"y", "a", "b", "back", "guide", "leftShoulder", "leftStick", "leftTrigger",
		"rightShoulder", "rightStick", "rightTrigger", "start", "x", "up", "down", "left", "right"};
	static final List<String> clickOnlyButtons = Arrays.asList("leftStick", "leftTrigger", "rightStick", "rightTrigger", "up",
		"down", "left", "right");
	static final int deadzone = 10, stickDistance = 80;
	static final int DPAD_NONE = 0, DPAD_DEADZONE = 2, DPAD_UP = 4, DPAD_DOWN = 8, DPAD_LEFT = 16, DPAD_RIGHT = 32;
	static final Timer timer = new Timer("PollController", true);

	private Device device;
	private XboxController controller;
	private PackedImages packedImages;
	private String overImageName;
	private int dragStartX = -1, dragStartY = -1;
	private int dpadDirection;
	private float lastTriggerValue, lastValueX, lastValueY;
	private Map<Target, Boolean> nameToStatus;
	private BufferedImage checkImage, xImage;
	private Listeners<Listener> listeners = new Listeners(Listener.class);
	private TimerTask pollControllerTask;
	private boolean isOver;
	private HashMap<Target, ArrayList<Float>> higlighted = new HashMap();

	private JInputXboxController.Listener controllerListener = new JInputXboxController.Listener() {
		public void buttonChanged (Button button, boolean pressed) {
			repaint();
		}

		public void axisChanged (Axis axis, float state) {
			repaint();
		}
	};

	private Device.Listener deviceListener = new Device.Listener() {
		public void buttonChanged (Button button, boolean pressed) {
			repaint();
		}

		public void axisChanged (Axis axis, float state) {
			repaint();
		}

		public void deviceReset () {
			repaint();
		}
	};

	public XboxControllerPanel () {
		for (Target target : Device.getTargets())
			higlighted.put(target, new ArrayList());

		setMinimumSize(new Dimension(497, 328));
		setMaximumSize(new Dimension(497, 328));
		setPreferredSize(new Dimension(497, 328));
		setOpaque(false);

		Sound.register("click");

		try {
			checkImage = ImageIO.read(getClass().getResource("/check.png"));
			xImage = ImageIO.read(getClass().getResource("/x.png"));
		} catch (IOException ex) {
			throw new RuntimeException("Image resources not found.", ex);
		}

		try {
			packedImages = new PackedImages("/controller.pack");
		} catch (IOException ex) {
			throw new RuntimeException(ex);
		}

		class MouseListener extends MouseAdapter implements MouseMotionListener {
			public void mouseMoved (MouseEvent event) {
				// Highlight buttons when moused over.
				int x = event.getX(), y = event.getY();
				String newOverButtonName = null;
				int closest = Integer.MAX_VALUE;
				for (String imageName : imageNames) {
					PackedImage packedImage = packedImages.get(imageName);
					if (x > packedImage.offsetX && x < packedImage.offsetX + packedImage.image.getWidth()) {
						if (y > packedImage.offsetY && y < packedImage.offsetY + packedImage.image.getHeight()) {
							int dx = Math.abs(x - (packedImage.offsetX + packedImage.image.getWidth() / 2));
							int dy = Math.abs(y - (packedImage.offsetY + packedImage.image.getHeight() / 2));
							int distance = dx * dx + dy * dy;
							if (distance < closest) {
								closest = distance;
								newOverButtonName = imageName;
							}
						}
					}
				}
				if (newOverButtonName != overImageName) {
					overImageName = newOverButtonName;
					repaint();
				}
			}

			public void mouseDragged (MouseEvent event) {
				// Drag to manipulate stick and trigger axes.
				if (overImageName == null) return;

				Object dragObject = getDragObject();
				if (dragObject == null) return;

				int x = event.getX(), y = event.getY();
				if (dragStartX == -1) {
					if (dragObject == Axis.leftTrigger || dragObject == Axis.rightTrigger) {
						dragStartX = x;
						dragStartY = y;
					} else if (dragObject == Axis.leftStickX) {
						dragStartX = 63;
						dragStartY = 180;
					} else if (dragObject == Axis.rightStickX) {
						dragStartX = 331;
						dragStartY = 264;
					} else if (dragObject instanceof Button) {
						dragStartX = 165;
						dragStartY = 250;
					}
					repaint();
				}

				if (dragObject == Axis.leftTrigger || dragObject == Axis.rightTrigger) {
					float value = Math.max(0, Math.min(stickDistance, y - dragStartY)) / (float)stickDistance;
					if (value != lastTriggerValue) {
						triggerDragged((Axis)dragObject, value);
						lastTriggerValue = value;
					}

				} else if (dragObject == Axis.leftStickX || dragObject == Axis.rightStickX) {
					float valueX = 0;
					if (Math.abs(x - dragStartX) > deadzone) {
						valueX = x - dragStartX;
						valueX -= valueX < 0 ? -deadzone : deadzone;
						valueX = Math.max(-stickDistance, Math.min(stickDistance, valueX)) / (float)stickDistance;
					}
					if (valueX != lastValueX) {
						stickDragged((Axis)dragObject, valueX);
						lastValueX = valueX;
					}

					float valueY = 0;
					if (Math.abs(y - dragStartY) > deadzone) {
						valueY = y - dragStartY;
						valueY -= valueY < 0 ? -deadzone : deadzone;
						valueY = Math.max(-stickDistance, Math.min(stickDistance, valueY)) / (float)stickDistance;
					}
					if (valueY != lastValueY) {
						Axis axisY = dragObject == Axis.leftStickX ? Axis.leftStickY : Axis.rightStickY;
						stickDragged(axisY, valueY);
						lastValueY = valueY;
					}

				} else if (dragObject instanceof Button) {
					int newDirection = 0;
					if (x > dragStartX + deadzone) newDirection |= DPAD_RIGHT;
					if (x < dragStartX - deadzone) newDirection |= DPAD_LEFT;
					if (y > dragStartY + deadzone) newDirection |= DPAD_DOWN;
					if (y < dragStartY - deadzone) newDirection |= DPAD_UP;
					if (newDirection == 0) newDirection = DPAD_DEADZONE;
					// If the direction has changed, press or release the dpad buttons.
					int diff = dpadDirection ^ newDirection;
					if ((diff & DPAD_RIGHT) != 0) dpadDragged(Button.right, (newDirection & DPAD_RIGHT) == DPAD_RIGHT);
					if ((diff & DPAD_LEFT) != 0) dpadDragged(Button.left, (newDirection & DPAD_LEFT) == DPAD_LEFT);
					if ((diff & DPAD_DOWN) != 0) dpadDragged(Button.down, (newDirection & DPAD_DOWN) == DPAD_DOWN);
					if ((diff & DPAD_UP) != 0) dpadDragged(Button.up, (newDirection & DPAD_UP) == DPAD_UP);
					dpadDirection = newDirection;
					repaint();
				}
			}

			public void mousePressed (MouseEvent event) {
				if (overImageName != null && !clickOnlyButtons.contains(overImageName))
					buttonClicked(Button.valueOf(overImageName), true);
			}

			public void mouseReleased (MouseEvent event) {
				if (dragStartX != -1) {
					dragStartX = dragStartY = -1;
					Object dragObject = getDragObject();
					if (dragObject == Axis.leftTrigger || dragObject == Axis.rightTrigger) {
						triggerDragged((Axis)dragObject, 0);
					} else if (dragObject == Axis.leftStickX || dragObject == Axis.rightStickX) {
						stickDragged((Axis)dragObject, 0);
						Axis axisY = dragObject == Axis.leftStickX ? Axis.leftStickY : Axis.rightStickY;
						stickDragged(axisY, 0);
					} else if (dragObject instanceof Button) {
						if ((dpadDirection & DPAD_RIGHT) == DPAD_RIGHT) buttonClicked(Button.right, false);
						if ((dpadDirection & DPAD_LEFT) == DPAD_LEFT) buttonClicked(Button.left, false);
						if ((dpadDirection & DPAD_DOWN) == DPAD_DOWN) buttonClicked(Button.down, false);
						if ((dpadDirection & DPAD_UP) == DPAD_UP) buttonClicked(Button.up, false);
						dpadDirection = DPAD_NONE;
					}
					repaint();
				}

				if (overImageName != null && !clickOnlyButtons.contains(overImageName))
					buttonClicked(Button.valueOf(overImageName), false);

				mouseMoved(event);
			}

			public void mouseClicked (MouseEvent event) {
				if (overImageName != null && clickOnlyButtons.contains(overImageName)) {
					if (overImageName.endsWith("Trigger")) {
						triggerDragged(Axis.valueOf(overImageName), 1);
						triggerDragged(Axis.valueOf(overImageName), 0);
						if (device != null) Sound.play("click");
					} else {
						Button stickButton = Button.valueOf(overImageName);
						buttonClicked(stickButton, true);
						try {
							Thread.sleep(32);
						} catch (InterruptedException ex) {
						}
						buttonClicked(stickButton, false);
					}
				}
			}

			public void mouseExited (MouseEvent event) {
				isOver = false;
				repaint();
			}

			public void mouseEntered (MouseEvent event) {
				isOver = true;
				repaint();
			}
		}
		MouseListener mouseListener = new MouseListener();
		addMouseMotionListener(mouseListener);
		addMouseListener(mouseListener);
		enableEvents(AWTEvent.MOUSE_WHEEL_EVENT_MASK);
	}

	void triggerDragged (Axis axis, float value) {
		repaint();
		try {
			if (device != null) device.apply(axis, value);
		} catch (IOException ex) {
			ex.printStackTrace();
		}
		notifyListeners(axis, value);
	}

	void stickDragged (Axis axis, float value) {
		repaint();
		try {
			if (device != null) device.apply(axis, value);
		} catch (IOException ex) {
			ex.printStackTrace();
		}
		notifyListeners(axis, value);
	}

	void buttonClicked (Button button, boolean pressed) {
		if (pressed && device != null) Sound.play("click");
		repaint();
		try {
			if (device != null) device.apply(button, pressed);
		} catch (IOException ex) {
			ex.printStackTrace();
		}
		notifyListeners(button, pressed);
	}

	void dpadDragged (Button button, boolean pressed) {
		repaint();
		try {
			if (device != null) device.apply(button, pressed);
		} catch (IOException ex) {
			ex.printStackTrace();
		}
		notifyListeners(button, pressed);
	}

	private void notifyListeners (Button button, boolean pressed) {
		Listener[] listeners = this.listeners.toArray();
		for (int i = 0, n = listeners.length; i < n; i++)
			listeners[i].buttonChanged(button, pressed);
	}

	private void notifyListeners (Axis axis, float state) {
		Listener[] listeners = this.listeners.toArray();
		for (int i = 0, n = listeners.length; i < n; i++)
			listeners[i].axisChanged(axis, state);
	}

	public void addListener (Listener listener) {
		listeners.addListener(listener);
	}

	public void removeListener (Listener listener) {
		listeners.removeListener(listener);
	}

	Object getDragObject () {
		if (overImageName == null) return null;
		if (overImageName.equals("leftStick")) return Axis.leftStickX;
		if (overImageName.equals("rightStick")) return Axis.rightStickX;
		if (overImageName.equals("leftTrigger")) return Axis.leftTrigger;
		if (overImageName.equals("rightTrigger")) return Axis.rightTrigger;
		if (overImageName.equals("up")) return Button.up;
		if (overImageName.equals("down")) return Button.down;
		if (overImageName.equals("left")) return Button.left;
		if (overImageName.equals("right")) return Button.right;
		return null;
	}

	protected void paintComponent (Graphics g) {
		if (!isOver && !Mouse.instance.isPressed()) {
			overImageName = null;
			dragStartX = -1;
			dpadDirection = DPAD_NONE;
		}

		g.setFont(g.getFont().deriveFont(10f));
		g.translate(0, -10);

		packedImages.get("controller").draw(g, 0, 0);

		for (Button button : Button.values())
			if (getTargetState(button) != 0) packedImages.get(button.name()).draw(g, 0, 0);

		if (device != null && dpadDirection != DPAD_NONE) {
			if ((dpadDirection & DPAD_LEFT) == DPAD_LEFT) packedImages.get("left").draw(g, 0, 0);
			if ((dpadDirection & DPAD_UP) == DPAD_UP) packedImages.get("up").draw(g, 0, 0);
			if ((dpadDirection & DPAD_RIGHT) == DPAD_RIGHT) packedImages.get("right").draw(g, 0, 0);
			if ((dpadDirection & DPAD_DOWN) == DPAD_DOWN) packedImages.get("down").draw(g, 0, 0);
		} else {
			if (overImageName != null) packedImages.get(overImageName).draw(g, 0, 0);
		}

		float leftTrigger = getTargetState(Axis.leftTrigger);
		float rightTrigger = getTargetState(Axis.rightTrigger);
		float leftStickX = getTargetState(Axis.leftStickX);
		float leftStickY = getTargetState(Axis.leftStickY);
		float rightStickX = getTargetState(Axis.rightStickX);
		float rightStickY = getTargetState(Axis.rightStickY);
		if (controller == null && device != null && dragStartX != -1) {
			Object dragObject = getDragObject();
			if (dragObject == Axis.leftTrigger)
				leftTrigger = lastTriggerValue;
			else if (dragObject == Axis.rightTrigger)
				rightTrigger = lastTriggerValue;
			else if (dragObject == Axis.leftStickX) {
				leftStickX = lastValueX;
				leftStickY = lastValueY;
			} else if (dragObject == Axis.rightStickX) {
				rightStickX = lastValueX;
				rightStickY = lastValueY;
			}
		}
		g.setColor(Color.black);
		drawTrigger(g, Axis.leftTrigger, leftTrigger);
		drawTrigger(g, Axis.rightTrigger, rightTrigger);
		drawStickArrows(g, Stick.left, leftStickX, leftStickY, false);
		drawStickArrows(g, Stick.right, rightStickX, rightStickY, false);

		for (Entry<Target, ArrayList<Float>> entry : higlighted.entrySet()) {
			Target target = entry.getKey();
			ArrayList<Float> values = entry.getValue();
			if (target instanceof Button || ((Axis)target).isTrigger()) {
				if (!values.isEmpty()) packedImages.get(target.name()).draw(g, 0, 0);
			} else {
				Axis axis = (Axis)target;
				if (axis.isX()) {
					for (float value : values)
						drawStickArrows(g, axis.getStick(), value, 0, true);
				} else {
					for (float value : values)
						drawStickArrows(g, axis.getStick(), 0, value, true);
				}
			}
		}

		g.setFont(g.getFont().deriveFont(Font.BOLD, 12f));
		if (controller != null)
			drawString(g, controller.toString(), 250, 38);
		else if (device != null) {
			drawString(g, device.toString(), 250, 32);
			for (Entry<String, String> entry : device.getTargetNames().entrySet()) {
				String alternateName = entry.getValue();
				if (alternateName.length() == 0) continue;
				String targetName = entry.getKey();
				PackedImage packedImage = packedImages.get(targetName);
				if (packedImage == null) continue;
				int x = packedImage.offsetX + packedImage.image.getWidth() / 2;
				int y = packedImage.offsetY + packedImage.image.getHeight() / 2;
				Target target = Device.getTarget(targetName);
				if (target == Button.leftShoulder || target == Button.rightShoulder)
					y -= 7;
				else if (target == Axis.leftTrigger) {
					if (leftTrigger != 0) continue;
					y -= 8;
				} else if (target == Axis.rightTrigger) {
					if (rightTrigger != 0) continue;
					y -= 8;
				} else if (target == Button.leftStick) {
					if (device != null && dragStartX != -1 && overImageName != null && overImageName.equals(targetName)) continue;
					y += 3;
					x -= 2;
				} else if (target == Button.rightStick) {
					if (device != null && dragStartX != -1 && overImageName != null && overImageName.equals(targetName)) continue;
					y += 4;
				}
				drawStringOutline(g, alternateName, x, y);
			}
		}

		if (nameToStatus != null) {
			// Show button status.
			for (Entry<Target, Boolean> entry : nameToStatus.entrySet()) {
				PackedImage packedImage = packedImages.get(entry.getKey().name());
				if (packedImage == null) continue;
				int x = packedImage.offsetX + packedImage.image.getWidth() / 2;
				int y = packedImage.offsetY + packedImage.image.getHeight() / 2;
				BufferedImage image = entry.getValue() ? checkImage : xImage;
				g.drawImage(image, x - (entry.getValue() ? 13 : 16), y - (entry.getValue() ? 24 : 16), null);
			}
			// Show axes status.
			drawStatusText(g, 25, 245, "X Axis", nameToStatus.get(Axis.leftStickX));
			drawStatusText(g, 25, 245 + 31, "Y Axis", nameToStatus.get(Axis.leftStickY));
			drawStatusText(g, 388, 245, "X Axis", nameToStatus.get(Axis.rightStickX));
			drawStatusText(g, 388, 245 + 31, "Y Axis", nameToStatus.get(Axis.rightStickY));
		}

		if (device != null && dragStartX != -1 && overImageName != null && !overImageName.endsWith("Trigger"))
			packedImages.get("crosshair").draw(g, dragStartX - 11, dragStartY);
	}

	private float getTargetState (Target target) {
		if (controller != null) return controller.get(target);
		if (device != null) return device.get(target);
		return 0;
	}

	public void setHighlighted (Target target, float value) {
		ArrayList<Float> values = higlighted.get(target);
		if (value == 0)
			values.clear();
		else
			values.add(value);
		repaint();
	}

	private void drawStatusText (Graphics g, int x, int y, String text, Boolean status) {
		if (status == null) return;
		g.drawString(text, x + 34, y + 21);
		g.drawImage(status ? checkImage : xImage, x, y, null);
	}

	private void drawTrigger (Graphics g, Axis axis, float value) {
		if ((int)(value * 100) == 0) return;
		packedImages.get(axis.name()).draw(g, 0, 0);
		drawString(g, toPercent(value), axis == Axis.leftTrigger ? 104 : 392, 32);
	}

	private void drawStickArrows (Graphics g, Stick stick, float valueX, float valueY, boolean isHighlight) {
		int x = stick == Stick.left ? 0 : 268;
		int y = stick == Stick.left ? 129 : 213;
		if ((int)(valueY * 100) != 0) {
			if (valueY < 0) {
				PackedImage arrowImage = packedImages.get("upArrow-green");
				arrowImage.draw(g, x, y);
				g.clipRect(x + arrowImage.offsetX, y + arrowImage.offsetY + 3, arrowImage.image.getWidth(), (int)((arrowImage.image
					.getHeight() - 18) * (1 - Math.abs(valueY))));
				packedImages.get("upArrow-white").draw(g, x, y);
				g.setClip(null);
				if (!isHighlight) drawString(g, toPercent(valueY), x + 62, y + 27);
			} else if (valueY > 0) {
				PackedImage arrowImage = packedImages.get("downArrow-green");
				arrowImage.draw(g, x, y);
				g.clipRect(x + arrowImage.offsetX, y + arrowImage.offsetY + 15
					+ (int)((arrowImage.image.getHeight() - 18) * Math.abs(valueY)), arrowImage.image.getWidth(), arrowImage.image
					.getHeight());
				packedImages.get("downArrow-white").draw(g, x, y);
				g.setClip(null);
				if (!isHighlight) drawString(g, toPercent(valueY), x + 62, y + 27 + 77);
			}
		}
		if ((int)(valueX * 100) != 0) {
			if (valueX < 0) {
				PackedImage arrowImage = packedImages.get("leftArrow-green");
				arrowImage.draw(g, x, y);
				g.clipRect(x + arrowImage.offsetX + 3, y + arrowImage.offsetY, (int)((arrowImage.image.getWidth() - 18) * (1 - Math
					.abs(valueX))), arrowImage.image.getHeight());
				packedImages.get("leftArrow-white").draw(g, x, y);
				g.setClip(null);
				if (!isHighlight) drawString(g, toPercent(valueX), x + 62 - 44, y + 27 + 39);
			} else if (valueX > 0) {
				PackedImage arrowImage = packedImages.get("rightArrow-green");
				arrowImage.draw(g, x, y);
				g.clipRect(x + arrowImage.offsetX + 15 + (int)((arrowImage.image.getWidth() - 18) * Math.abs(valueX)), y
					+ arrowImage.offsetY, (int)((arrowImage.image.getWidth() - 18) * (1 - Math.abs(valueX))), arrowImage.image
					.getHeight());
				packedImages.get("rightArrow-white").draw(g, x, y);
				g.setClip(null);
				if (!isHighlight) drawString(g, toPercent(valueX), x + 62 + 43, y + 27 + 39);
			}
		}
	}

	private String toPercent (float value) {
		return String.valueOf((int)(value * 100));
	}

	private void drawString (Graphics g, String text, int x, int y) {
		FontMetrics metrics = g.getFontMetrics();
		x -= metrics.stringWidth(text) / 2;
		g.drawString(text, x, y);
	}

	private void drawStringOutline (Graphics g, String text, int x, int y) {
		FontMetrics metrics = g.getFontMetrics();
		x -= metrics.stringWidth(text) / 2;
		y += metrics.getAscent() / 2 - 1;
		g.setColor(Color.black);
		g.drawString(text, x + 1, y + 1);
		g.drawString(text, x + 1, y - 1);
		g.drawString(text, x - 1, y + 1);
		g.drawString(text, x - 1, y - 1);
		g.setColor(Color.white);
		g.drawString(text, x, y);
	}

	public void setDevice (Device device) {
		if (this.device != null) this.device.removeListener(deviceListener);
		this.device = device;
		if (device != null) device.addListener(deviceListener);
		repaint();
	}

	public void setController (final XboxController controller) {
		if (pollControllerTask != null) pollControllerTask.cancel();
		if (controller != null) {
			timer.scheduleAtFixedRate(pollControllerTask = new TimerTask() {
				public void run () {
					controller.poll();
				}
			}, 0, 64);
		}

		if (this.controller != null) this.controller.removeListener(controllerListener);
		this.controller = controller;
		if (controller != null) controller.addListener(controllerListener);
		repaint();
	}

	public void setStatus (Map<Target, Boolean> nameToStatus) {
		this.nameToStatus = nameToStatus;
		repaint();
	}

	static public class Listener {
		public void buttonChanged (Button button, boolean pressed) {
		}

		public void axisChanged (Axis axis, float state) {
		}
	}
}
