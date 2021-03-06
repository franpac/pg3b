
package com.esotericsoftware.controller.ui;

import com.esotericsoftware.controller.device.Deadzone;
import com.esotericsoftware.controller.input.Input;
import com.esotericsoftware.controller.input.Keyboard;

/**
 * A trigger that executes its action based on the state of a JInput controller (keyboard, mouse, joystick, etc, essentially any
 * peripheral).
 */
public class InputTrigger extends Trigger {
	private Input input;
	private boolean shift, ctrl, alt, noModifiers, invert;
	private Deadzone deadzone;

	public InputTrigger () {
	}

	public InputTrigger (Input input, Action action) {
		setInput(input);
		setAction(action);
	}

	public String getSourceName () {
		if (input == null) return "";
		return input.getInputDevice().toString();
	}

	public Input getInput () {
		return input;
	}

	public void setInput (Input input) {
		this.input = input;
	}

	public boolean getShift () {
		return shift;
	}

	public void setShift (boolean shift) {
		this.shift = shift;
	}

	public boolean getCtrl () {
		return ctrl;
	}

	public void setCtrl (boolean ctrl) {
		this.ctrl = ctrl;
	}

	public boolean getAlt () {
		return alt;
	}

	public void setAlt (boolean alt) {
		this.alt = alt;
	}

	public boolean getNoModifiers () {
		return noModifiers;
	}

	public void setNoModifiers (boolean noModifiers) {
		this.noModifiers = noModifiers;
	}

	public boolean getInvert () {
		return invert;
	}

	/**
	 * If true, the payload will be inverted. Only affects
	 */
	public void setInvert (boolean invert) {
		this.invert = invert;
	}

	public Deadzone getDeadzone () {
		return deadzone;
	}

	public void setDeadzone (Deadzone deadzone) {
		this.deadzone = deadzone;
	}

	public boolean isValid () {
		if (input == null) return false;
		return input.isValid();
	}

	public Poller getPoller () {
		if (input == null) return null;
		return input.getInputDevice();
	}

	/**
	 * Returns true if the current keyboard state meets the requirements for this trigger's keyboard modifiers.
	 */
	public boolean checkModifiers () {
		Keyboard keyboard = Keyboard.instance;
		if (noModifiers) {
			if (keyboard.isCtrlDown()) return false;
			if (keyboard.isAltDown()) return false;
			if (keyboard.isShiftDown()) return false;
		} else {
			if (ctrl && !keyboard.isCtrlDown()) return false;
			if (alt && !keyboard.isAltDown()) return false;
			if (shift && !keyboard.isShiftDown()) return false;
		}
		return true;
	}

	public boolean isActive () {
		if (input == null) return false;		
		return getPayload() != 0 && checkModifiers();
	}

	public Float getPayload () {
		if (input == null) return null;
		float payload = input.getState();
		if (input.isAxis()) {
			if (invert) payload = -payload;
			if (deadzone != null) {
				float x, y;
				if (input.isAxisX()) {
					x = payload;
					y = input.getOtherState();
				} else {
					x = input.getOtherState();
					y = payload;
				}
				float[] values = deadzone.getInput(x, y);
				payload = input.isAxisX() ? values[0] : values[1];
			}
		}
		return payload;
	}

	public String toString () {
		StringBuilder buffer = new StringBuilder();
		if (getCtrl()) buffer.append("ctrl+");
		if (getAlt()) buffer.append("alt+");
		if (getShift()) buffer.append("shift+");
		if (input == null)
			buffer.append("<none>");
		else
			buffer.append(input);
		return buffer.toString();
	}

	public int hashCode () {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + (alt ? 1231 : 1237);
		result = prime * result + (ctrl ? 1231 : 1237);
		result = prime * result + ((deadzone == null) ? 0 : deadzone.hashCode());
		result = prime * result + ((input == null) ? 0 : input.hashCode());
		result = prime * result + (invert ? 1231 : 1237);
		result = prime * result + (noModifiers ? 1231 : 1237);
		result = prime * result + (shift ? 1231 : 1237);
		return result;
	}

	public boolean equals (Object obj) {
		if (this == obj) return true;
		if (!super.equals(obj)) return false;
		if (getClass() != obj.getClass()) return false;
		InputTrigger other = (InputTrigger)obj;
		if (alt != other.alt) return false;
		if (ctrl != other.ctrl) return false;
		if (deadzone == null) {
			if (other.deadzone != null) return false;
		} else if (!deadzone.equals(other.deadzone)) return false;
		if (input == null) {
			if (other.input != null) return false;
		} else if (!input.equals(other.input)) return false;
		if (invert != other.invert) return false;
		if (noModifiers != other.noModifiers) return false;
		if (shift != other.shift) return false;
		return true;
	}
}
