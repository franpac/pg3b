
package com.esotericsoftware.controller.input;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.Arrays;
import java.util.List;

import com.esotericsoftware.controller.device.Axis;
import com.esotericsoftware.controller.device.Button;

/**
 * Reads the state from an Xbox 360 controller using XInput (Windows only).
 */
public class XInputXboxController extends XboxController {
	static {
		if (System.getProperty("sun.arch.data.model", "").equals("64")) {
			try {
				System.loadLibrary("xinput64");
			} catch (UnsatisfiedLinkError ex64) {
				try {
					System.loadLibrary("xinput32");
				} catch (UnsatisfiedLinkError ignored) {
					throw ex64;
				}
			}
		} else {
			try {
				System.loadLibrary("xinput32");
			} catch (UnsatisfiedLinkError ex32) {
				try {
					System.loadLibrary("xinput64");
				} catch (UnsatisfiedLinkError ignored) {
					throw ex32;
				}
			}
		}
	}

	static private final int BUTTON_DPAD_UP = 0x1;
	static private final int BUTTON_DPAD_DOWN = 0x2;
	static private final int BUTTON_DPAD_LEFT = 0x4;
	static private final int BUTTON_DPAD_RIGHT = 0x8;
	static private final int BUTTON_START = 0x10;
	static private final int BUTTON_BACK = 0x20;
	static private final int BUTTON_LEFT_THUMB = 0x40;
	static private final int BUTTON_RIGHT_THUMB = 0x80;
	static private final int BUTTON_LEFT_SHOULDER = 0x100;
	static private final int BUTTON_RIGHT_SHOULDER = 0x200;
	static private final int BUTTON_A = 0x1000;
	static private final int BUTTON_B = 0x2000;
	static private final int BUTTON_X = 0x4000;
	static private final int BUTTON_Y = 0x8000;

	static private final XInputXboxController[] controllers = {new XInputXboxController(0), new XInputXboxController(1),
		new XInputXboxController(2), new XInputXboxController(3)};

	private final int player;
	private final ByteBuffer byteBuffer;
	private final ShortBuffer shortBuffer;
	private boolean isConnected;
	private int buttons;
	private int leftTrigger, rightTrigger;
	private int thumbLX, thumbLY;
	private int thumbRX, thumbRY;

	private XInputXboxController (int playerNumber) {
		this.player = playerNumber;

		byteBuffer = ByteBuffer.allocateDirect(16);
		byteBuffer.order(ByteOrder.nativeOrder());
		shortBuffer = byteBuffer.asShortBuffer();
	}

	public synchronized boolean poll () {
		poll(player, byteBuffer);

		boolean wasConnected = isConnected;
		isConnected = shortBuffer.get(0) != 0;
		if (!isConnected) shortBuffer.clear();

		int oldButtons = buttons;
		int oldLeftTrigger = leftTrigger;
		int oldRightTrigger = rightTrigger;
		int oldThumbLX = thumbLX;
		int oldThumbLY = thumbLY;
		int oldThumbRX = thumbRX;
		int oldThumbRY = thumbRY;

		buttons = shortBuffer.get(1);
		leftTrigger = shortBuffer.get(2);
		rightTrigger = shortBuffer.get(3);
		thumbLX = shortBuffer.get(4);
		thumbLY = shortBuffer.get(5);
		thumbRX = shortBuffer.get(6);
		thumbRY = shortBuffer.get(7);

		int diff = oldButtons ^ buttons;
		if ((diff & BUTTON_DPAD_RIGHT) != 0) notifyListeners(Button.right, (buttons & BUTTON_DPAD_RIGHT) == BUTTON_DPAD_RIGHT);
		if ((diff & BUTTON_DPAD_LEFT) != 0) notifyListeners(Button.left, (buttons & BUTTON_DPAD_LEFT) == BUTTON_DPAD_LEFT);
		if ((diff & BUTTON_DPAD_DOWN) != 0) notifyListeners(Button.down, (buttons & BUTTON_DPAD_DOWN) == BUTTON_DPAD_DOWN);
		if ((diff & BUTTON_DPAD_UP) != 0) notifyListeners(Button.up, (buttons & BUTTON_DPAD_UP) == BUTTON_DPAD_UP);
		if ((diff & BUTTON_START) != 0) notifyListeners(Button.start, (buttons & BUTTON_START) == BUTTON_START);
		if ((diff & BUTTON_BACK) != 0) notifyListeners(Button.back, (buttons & BUTTON_BACK) == BUTTON_BACK);
		if ((diff & BUTTON_LEFT_THUMB) != 0) notifyListeners(Button.leftStick, (buttons & BUTTON_LEFT_THUMB) == BUTTON_LEFT_THUMB);
		if ((diff & BUTTON_RIGHT_THUMB) != 0)
			notifyListeners(Button.rightStick, (buttons & BUTTON_RIGHT_THUMB) == BUTTON_RIGHT_THUMB);
		if ((diff & BUTTON_LEFT_SHOULDER) != 0)
			notifyListeners(Button.leftShoulder, (buttons & BUTTON_LEFT_SHOULDER) == BUTTON_LEFT_SHOULDER);
		if ((diff & BUTTON_RIGHT_SHOULDER) != 0)
			notifyListeners(Button.rightShoulder, (buttons & BUTTON_RIGHT_SHOULDER) == BUTTON_RIGHT_SHOULDER);
		if ((diff & BUTTON_A) != 0) notifyListeners(Button.a, (buttons & BUTTON_A) == BUTTON_A);
		if ((diff & BUTTON_B) != 0) notifyListeners(Button.b, (buttons & BUTTON_B) == BUTTON_B);
		if ((diff & BUTTON_X) != 0) notifyListeners(Button.x, (buttons & BUTTON_X) == BUTTON_X);
		if ((diff & BUTTON_Y) != 0) notifyListeners(Button.y, (buttons & BUTTON_Y) == BUTTON_Y);

		if (leftTrigger != oldLeftTrigger) notifyListeners(Axis.leftTrigger, leftTrigger / 255f);
		if (rightTrigger != oldRightTrigger) notifyListeners(Axis.rightTrigger, rightTrigger / 255f);
		if (thumbLX != oldThumbLX) notifyListeners(Axis.leftStickX, thumbLX / 32767f);
		if (thumbLY != oldThumbLY) notifyListeners(Axis.leftStickY, thumbLY / 32767f);
		if (thumbRX != oldThumbRX) notifyListeners(Axis.rightStickX, thumbRX / 32767f);
		if (thumbRY != oldThumbRY) notifyListeners(Axis.rightStickY, thumbRY / 32767f);

		if (!wasConnected && isConnected)
			notifyConnected();
		else if (wasConnected && !isConnected) {
			notifyDisconnected();
		}

		return isConnected;
	}

	public float get (Axis axis) {
		if (!poll()) return 0;
		switch (axis) {
		case leftStickX:
			return thumbLX / 32767f;
		case leftStickY:
			return thumbLY / -32767f;
		case rightStickX:
			return thumbRX / 32767f;
		case rightStickY:
			return thumbRY / -32767f;
		case leftTrigger:
			return leftTrigger / 255f;
		case rightTrigger:
			return rightTrigger / 255f;
		}
		throw new RuntimeException();
	}

	public boolean get (Button button) {
		if (!poll()) return false;
		switch (button) {
		case up:
			return (buttons & BUTTON_DPAD_UP) == BUTTON_DPAD_UP;
		case down:
			return (buttons & BUTTON_DPAD_DOWN) == BUTTON_DPAD_DOWN;
		case left:
			return (buttons & BUTTON_DPAD_LEFT) == BUTTON_DPAD_LEFT;
		case right:
			return (buttons & BUTTON_DPAD_RIGHT) == BUTTON_DPAD_RIGHT;
		case start:
			return (buttons & BUTTON_START) == BUTTON_START;
		case guide:
			// The Xbox controller driver doesn't expose this button!
			return false;
		case a:
			return (buttons & BUTTON_A) == BUTTON_A;
		case b:
			return (buttons & BUTTON_B) == BUTTON_B;
		case x:
			return (buttons & BUTTON_X) == BUTTON_X;
		case y:
			return (buttons & BUTTON_Y) == BUTTON_Y;
		case leftShoulder:
			return (buttons & BUTTON_LEFT_SHOULDER) == BUTTON_LEFT_SHOULDER;
		case rightShoulder:
			return (buttons & BUTTON_RIGHT_SHOULDER) == BUTTON_RIGHT_SHOULDER;
		case back:
			return (buttons & BUTTON_BACK) == BUTTON_BACK;
		case leftStick:
			return (buttons & BUTTON_LEFT_THUMB) == BUTTON_LEFT_THUMB;
		case rightStick:
			return (buttons & BUTTON_RIGHT_THUMB) == BUTTON_RIGHT_THUMB;
		}
		throw new RuntimeException();
	}

	public boolean isConnected () {
		return isConnected;
	}

	public int getPort () {
		return player + 1;
	}

	public ControllerInput getLastInput () {
		if (lastButton != null) return new ControllerInput(player, lastButton);
		if (lastAxis != null) return new ControllerInput(player, lastAxis);
		return null;
	}

	static public class ControllerInput implements Input {
		private int player;
		private Button button;
		private Axis axis;

		public ControllerInput () {
		}

		public ControllerInput (int player, Button button) {
			this.player = player;
			this.button = button;
		}

		public ControllerInput (int player, Axis axis) {
			this.player = player;
			this.axis = axis;
		}

		public float getState () {
			if (axis != null) return controllers[player].get(axis);
			if (button != null) return controllers[player].get(button) ? 1 : 0;
			return 0;
		}

		public float getOtherState () {
			switch (axis) {
			case leftStickX:
				return controllers[player].get(Axis.leftStickY);
			case leftStickY:
				return controllers[player].get(Axis.leftStickX);
			case rightStickX:
				return controllers[player].get(Axis.rightStickY);
			case rightStickY:
				return controllers[player].get(Axis.rightStickX);
			default:
				return 0;
			}
		}

		public XInputXboxController getInputDevice () {
			return controllers[player];
		}

		public boolean isValid () {
			return controllers[player].isConnected();
		}

		public boolean isAxis () {
			return axis != null && !axis.isTrigger();
		}

		public boolean isAxisX () {
			return axis == Axis.leftStickX || axis == Axis.rightStickX;
		}

		public String toString () {
			if (button != null) return button.toString();
			if (axis != null) return axis.toString();
			return "<none>";
		}

		public int hashCode () {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((axis == null) ? 0 : axis.hashCode());
			result = prime * result + ((button == null) ? 0 : button.hashCode());
			result = prime * result + player;
			return result;
		}

		public boolean equals (Object obj) {
			if (this == obj) return true;
			if (obj == null) return false;
			if (getClass() != obj.getClass()) return false;
			ControllerInput other = (ControllerInput)obj;
			if (axis == null) {
				if (other.axis != null) return false;
			} else if (!axis.equals(other.axis)) return false;
			if (button == null) {
				if (other.button != null) return false;
			} else if (!button.equals(other.button)) return false;
			if (player != other.player) return false;
			return true;
		}
	}

	static public List<XInputXboxController> getXInputControllers () {
		return Arrays.asList(controllers);
	}

	native static private void poll (int index, ByteBuffer buffer);

	native static public void setEnabled (boolean enabled);
}
