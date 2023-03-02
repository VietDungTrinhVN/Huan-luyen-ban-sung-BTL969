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

package com.shootoff.headless.protocol;

import java.util.UUID;

public class ResizeTargetMessage extends TargetMessage {
	private final double newWidth;
	private final double newHeight;

	public ResizeTargetMessage(UUID uuid, double newWidth, double newHeight) {
		super(uuid);
		this.newWidth = newWidth;
		this.newHeight = newHeight;
	}

	public double getNewWidth() {
		return newWidth;
	}

	public double getNewHeight() {
		return newHeight;
	}
}
