
package com.esotericsoftware.controller.ui;

import java.io.IOException;

import com.esotericsoftware.controller.device.Axis;
import com.esotericsoftware.controller.device.Button;
import com.esotericsoftware.controller.device.Device;
import com.esotericsoftware.controller.device.Target;
import com.esotericsoftware.controller.input.Input;
import com.esotericsoftware.controller.input.Mouse;
import com.esotericsoftware.controller.ui.swing.UI;

/**
 * An action that sets the state of a button or axis when executed.
 */
public class DeviceAction implements Action {
	private Target target;
	private Direction direction = Direction.both;

	public DeviceAction () {
	}

	public DeviceAction (Target target) {
		setTarget(target);
	}

	public Target getTarget () {
		return target;
	}

	public void setTarget (Target target) {
		if (target != null && !(target instanceof Button) && !(target instanceof Axis))
			throw new IllegalArgumentException("target must be a button or axis.");
		this.target = target;
	}

	public Direction getDirection () {
		return direction;
	}

	/**
	 * Sets the axis direction required for this action to execute.
	 */
	public void setDirection (Direction direction) {
		this.direction = direction;
	}

	public boolean isValid () {
		return UI.instance.getDevice() != null;
	}

	public void reset (Config config, Trigger trigger) {
	}

	public Object execute (Config config, Trigger trigger, boolean isActive, Object object) {
		Device device = UI.instance.getDevice();
		if (device == null) return null;
		float payload;
		if (object instanceof Float)
			payload = (Float)object;
		else if (object instanceof Integer)
			payload = (Integer)object;
		else if (object instanceof Boolean)
			payload = (Boolean)object ? 1 : 0;
		else {
			payload = object != null ? 1 : 0;
		}
		switch (direction) {
		case up:
			if (payload > 0) payload = -payload;
		case left:
			if (payload > 0) payload = -payload;
		}
		// If the target is an axis and the trigger was activated by a mouse axis...
		if (target instanceof Axis && trigger instanceof InputTrigger) {
			Input input = ((InputTrigger)trigger).getInput();
			if (input instanceof Mouse.MouseInput) {
				if (input.isAxis()) {
					float deltaX = 0, deltaY = 0;
					if (input.isAxisX())
						deltaX = payload;
					else
						deltaY = payload;
					device.addMouseDelta(((Axis)target).getStick(), deltaX, deltaY);
					return payload;
				}
			}
		}
		device.set(target, payload);
		return payload;
	}

	public String getType () {
		return "Device";
	}

	public int hashCode () {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((direction == null) ? 0 : direction.hashCode());
		result = prime * result + ((target == null) ? 0 : target.hashCode());
		return result;
	}

	public boolean equals (Object obj) {
		if (this == obj) return true;
		if (obj == null) return false;
		if (getClass() != obj.getClass()) return false;
		DeviceAction other = (DeviceAction)obj;
		if (direction == null) {
			if (other.direction != null) return false;
		} else if (!direction.equals(other.direction)) return false;
		if (target == null) {
			if (other.target != null) return false;
		} else if (!target.equals(other.target)) return false;
		return true;
	}

	public String toString () {
		if (target == null) return "";
		StringBuilder buffer = new StringBuilder();
		buffer.append(target);
		if (direction != Direction.both) {
			buffer.append(' ');
			buffer.append(direction);
		}
		return buffer.toString();
	}

	static public enum Direction {
		up, down, left, right, both;

		public boolean isNegative () {
			return this == up || this == left;
		}
	}
}
