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

package com.shootoff.gui.targets;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.shootoff.config.Configuration;
import com.shootoff.gui.CanvasManager;
import com.shootoff.targets.Hit;
import com.shootoff.targets.ImageRegion;
import com.shootoff.targets.RectangleRegion;
import com.shootoff.targets.RegionType;
import com.shootoff.targets.Target;
import com.shootoff.targets.TargetRegion;
import com.shootoff.targets.animation.SpriteAnimation;

import javafx.animation.Animation.Status;
import com.shootoff.util.SwingFXUtils;
import javafx.geometry.Bounds;
import javafx.geometry.Dimension2D;
import javafx.geometry.Point2D;
import javafx.scene.Cursor;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.image.Image;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Shape;

/**
 * This is contains the code required to display, resize, and move targets. It
 * also implements required functions like animating targets and determine if a
 * target was hit and where if it was hit. This class needs to be re-implemented
 * to make PhanMemBanSung work on platforms that don't support JavaFX.
 * 
 * @author phrack
 */
public class TargetView implements Target {
	private static final Logger logger = LoggerFactory.getLogger(TargetView.class);

	private static final double ANCHOR_WIDTH = 10;
	private static final double ANCHOR_HEIGHT = ANCHOR_WIDTH;

	protected static final int MOVEMENT_DELTA = 1;
	protected static final int SCALE_DELTA = 1;
	private static final int RESIZE_MARGIN = 5;

	private final File targetFile;
	private final Group targetGroup;
	private final Map<String, String> targetTags;
	private final Set<Node> resizeAnchors = new HashSet<>();
	private final Optional<Configuration> config;
	private final Optional<CanvasManager> parent;
	private final Optional<List<Target>> targets;
	private final boolean userDeletable;
	private final String cameraName;
	private boolean keepInBounds = false;
	private boolean isSelected = false;
	private boolean move;
	private boolean resize;
	private boolean top;
	private boolean bottom;
	private boolean left;
	private boolean right;
	private double x;
	private double y;

	private final double origWidth;
	private final double origHeight;

	private TargetSelectionListener selectionListener;

	public TargetView(File targetFile, Group target, Map<String, String> targetTags, CanvasManager parent,
			boolean userDeletable) {
		this.targetFile = targetFile;
		targetGroup = target;
		this.targetTags = targetTags;
		config = Optional.ofNullable(Configuration.getConfig());
		this.parent = Optional.of(parent);
		targets = Optional.empty();
		this.userDeletable = userDeletable;
		cameraName = parent.getCameraName();
		origWidth = targetGroup.getBoundsInParent().getWidth();
		origHeight = targetGroup.getBoundsInParent().getHeight();

		targetGroup.setOnMouseClicked((event) -> {
			// Skip target selection if click to shoot is being used
			if (config.isPresent() && config.get().inDebugMode() && (event.isShiftDown() || event.isControlDown()))
				return;

			parent.toggleTargetSelection(Optional.of(this));
			targetGroup.requestFocus();
			event.consume();
		});

		mousePressed();
		mouseDragged();
		mouseMoved();
		mouseReleased();
		keyPressed();
	}

	// Used by the session viewer, target pane, and for testing
	public TargetView(Group target, Map<String, String> targetTags, List<Target> targets) {
		targetFile = null;
		targetGroup = target;
		this.targetTags = targetTags;
		config = Optional.empty();
		parent = Optional.empty();
		this.targets = Optional.of(targets);
		userDeletable = false;
		cameraName = null;
		origWidth = targetGroup.getBoundsInParent().getWidth();
		origHeight = targetGroup.getBoundsInParent().getHeight();

		mousePressed();
		mouseDragged();
		mouseMoved();
		mouseReleased();
		keyPressed();

	}

	public boolean isUserDeletable() {
		return userDeletable;
	}

	@Override
	public File getTargetFile() {
		return targetFile;
	}

	public Group getTargetGroup() {
		return targetGroup;
	}

	@Override
	public int getTargetIndex() {
		if (parent.isPresent())
			return parent.get().getTargets().indexOf(this);
		else
			return -1;
	}

	@Override
	public void fillParent() {
		if (parent.isPresent()) {
			final Bounds b = parent.get().getCanvasGroup().getBoundsInParent();
			setDimensions(b.getWidth(), b.getHeight());
			final Point2D p = targetGroup.localToParent(0, 0);
			setPosition(p.getX() * -1, p.getY() * -1);
		}
	}

	@Override
	public void addTargetChild(Node child) {
		getTargetGroup().getChildren().add(child);
	}

	@Override
	public void removeTargetChild(Node child) {
		getTargetGroup().getChildren().remove(child);
	}

	@Override
	public List<TargetRegion> getRegions() {
		final List<TargetRegion> regions = new ArrayList<>();

		for (final Node n : getTargetGroup().getChildren()) {
			if (n instanceof TargetRegion) regions.add((TargetRegion) n);
		}

		return regions;
	}

	@Override
	public boolean hasRegion(TargetRegion region) {
		return getTargetGroup().getChildren().contains(region);
	}

	@Override
	public void setVisible(boolean isVisible) {
		getTargetGroup().setVisible(isVisible);
	}

	@Override
	public boolean isVisible() {
		return getTargetGroup().isVisible();
	}

	@Override
	public void setPosition(double x, double y) {
		targetGroup.setLayoutX(x);
		targetGroup.setLayoutY(y);

		if (config.isPresent() && config.get().getSessionRecorder().isPresent()) {
			config.get().getSessionRecorder().get().recordTargetMoved(cameraName, this, (int) targetGroup.getLayoutX(),
					(int) targetGroup.getLayoutY());
		}
	}

	@Override
	public Point2D getPosition() {
		return new Point2D(targetGroup.getLayoutX(), targetGroup.getLayoutY());
	}

	@Override
	public void setDimensions(double newWidth, double newHeight) {
		final double currentWidth = targetGroup.getBoundsInParent().getWidth();
		final double currentHeight = targetGroup.getBoundsInParent().getHeight();

		if (Math.abs(currentWidth - newWidth) > .001) {
			final double scaleXDelta = 1.0 + ((newWidth - currentWidth) / currentWidth);
			targetGroup.setScaleX(targetGroup.getScaleX() * scaleXDelta);

			// Keep unresizable regions the same size
			for (final Node n : targetGroup.getChildren()) {
				final TargetRegion r = (TargetRegion) n;

				if (r.tagExists(Target.TAG_RESIZABLE) && !Boolean.parseBoolean(r.getTag(Target.TAG_RESIZABLE))) {
					final double width = n.getBoundsInParent().getWidth();
					final double scaledPercentChange = (width / (width * targetGroup.getScaleX()));

					n.setScaleX(scaledPercentChange);
				}
			}
		}

		if (Math.abs(currentHeight - newHeight) > .001) {
			final double scaleYDelta = 1.0 + ((newHeight - currentHeight) / currentHeight);
			targetGroup.setScaleY(targetGroup.getScaleY() * scaleYDelta);

			// Keep unresizable regions the same size
			for (final Node n : targetGroup.getChildren()) {
				final TargetRegion r = (TargetRegion) n;

				if (r.tagExists(Target.TAG_RESIZABLE) && !Boolean.parseBoolean(r.getTag(Target.TAG_RESIZABLE))) {
					final double height = n.getBoundsInParent().getHeight();
					final double scaledPercentChange = (height / (height * targetGroup.getScaleY()));

					n.setScaleY(scaledPercentChange);
				}
			}
		}
	}

	@Override
	public Dimension2D getDimension() {
		return new Dimension2D(targetGroup.getBoundsInParent().getWidth(), targetGroup.getBoundsInParent().getHeight());
	}

	@Override
	public double getScaleX() {
		return targetGroup.getBoundsInParent().getWidth() / origWidth;
	}

	@Override
	public double getScaleY() {
		return targetGroup.getBoundsInParent().getHeight() / origHeight;
	}

	@Override
	public void scale(double widthFactor, double heightFactor) {
		final double newWidth = getDimension().getWidth() * widthFactor;
		final double widthDelta = newWidth - getDimension().getWidth();
		final double newX = getBoundsInParent().getMinX() * widthFactor;
		final double deltaX = newX - getBoundsInParent().getMinX() + (widthDelta / 2);

		final double newHeight = getDimension().getHeight() * heightFactor;
		final double heightDelta = newHeight - getDimension().getHeight();
		final double newY = getBoundsInParent().getMinY() * heightFactor;
		final double deltaY = newY - getBoundsInParent().getMinY() + (heightDelta / 2);

		setPosition(getPosition().getX() + deltaX, getPosition().getY() + deltaY);

		setDimensions(newWidth, newHeight);
	}

	@Override
	public Bounds getBoundsInParent() {
		return targetGroup.getBoundsInParent();
	}

	@Override
	public Point2D parentToLocal(double x, double y) {
		return getTargetGroup().parentToLocal(x, y);
	}

	@Override
	public void setClip(Rectangle clip) {
		getTargetGroup().setClip(clip);
	}

	/**
	 * Sets whether or not the target should stay in the bounds of its parent.
	 * 
	 * @param keepInBounds
	 *            <tt>true</tt> if the target should stay in bounds,
	 *            <tt>false</tt> otherwise.
	 */
	public void setKeepInBounds(boolean keepInBounds) {
		this.keepInBounds = keepInBounds;
	}

	public boolean getKeepInBounds() {
		return keepInBounds;
	}

	public static void parseCommandTag(TargetRegion region, CommandProcessor commandProcessor) {
		if (!region.tagExists("command")) return;

		final String commandsSource = region.getTag("command");
		final List<String> commands = Arrays.asList(commandsSource.split(";"));

		for (final String command : commands) {
			final int openParen = command.indexOf('(');
			String commandName;
			List<String> args;

			if (openParen > 0) {
				commandName = command.substring(0, openParen);
				args = Arrays.asList(command.substring(openParen + 1, command.indexOf(')')).split(","));
			} else {
				commandName = command;
				args = new ArrayList<>();
			}

			commandProcessor.process(commands, commandName, args);
		}
	}

	public static Optional<TargetRegion> getTargetRegionByName(List<Target> targets, TargetRegion region, String name) {
		for (final Target target : targets) {
			if (target.hasRegion(region)) {
				for (final TargetRegion r : target.getRegions()) {
					if (r.tagExists("name") && r.getTag("name").equals(name)) return Optional.of(r);
				}
			}
		}

		return Optional.empty();
	}

	@Override
	public void animate(TargetRegion region, List<String> args) {
		ImageRegion imageRegion;

		boolean resetAfterAnimation = false;

		if (args.size() == 0) {
			imageRegion = (ImageRegion) region;
		} else if (args.get(0).equals("true")) {
			imageRegion = (ImageRegion) region;
			resetAfterAnimation = true;
		} else {
			Optional<TargetRegion> r;

			if (targets.isPresent()) {
				r = getTargetRegionByName(targets.get(), region, args.get(0));
			} else if (parent.isPresent()) {
				r = getTargetRegionByName(parent.get().getTargets(), region, args.get(0));
			} else {
				r = Optional.empty();
			}

			if (r.isPresent()) {
				imageRegion = (ImageRegion) r.get();
			} else {
				logger.error("Request to animate region named {}, but it doesn't exist.", args.get(0));
				return;
			}
		}

		// Don't repeat animations for fallen targets
		if (!imageRegion.onFirstFrame()) return;

		if (imageRegion.getAnimation().isPresent()) {
			final SpriteAnimation animation = imageRegion.getAnimation().get();
			animation.play();

			if (resetAfterAnimation) {
				animation.setOnFinished((e) -> {
					animation.reset();
					animation.setOnFinished(null);
				});
			}
		} else {
			logger.error("Request to animate region, but region does not contain an animation.");
		}
	}

	@Override
	public void reverseAnimation(TargetRegion region) {
		if (region.getType() != RegionType.IMAGE) {
			logger.error("A reversal was requested on a non-image region.");
			return;
		}

		final ImageRegion imageRegion = (ImageRegion) region;
		if (imageRegion.getAnimation().isPresent()) {
			final SpriteAnimation animation = imageRegion.getAnimation().get();

			if (animation.getStatus() == Status.RUNNING) {
				animation.setOnFinished((e) -> {
					animation.reverse();
					animation.setOnFinished(null);
				});
			} else {
				animation.reverse();
			}
		} else {
			logger.error("A reversal was requested on an image region that isn't animated.");
		}
	}

	public void toggleSelected() {
		isSelected = !isSelected;

		final Color stroke = isSelected ? TargetRegion.SELECTED_STROKE_COLOR : TargetRegion.UNSELECTED_STROKE_COLOR;

		for (final Node node : getTargetGroup().getChildren()) {
			if (!(node instanceof TargetRegion)) continue;

			final TargetRegion region = (TargetRegion) node;
			if (region.getType() != RegionType.IMAGE) {
				((Shape) region).setStroke(stroke);
			}
		}

		if (isSelected) {
			addResizeAnchors();
		} else {
			getTargetGroup().getChildren().removeAll(resizeAnchors);
			resizeAnchors.clear();
		}

		if (selectionListener != null) selectionListener.targetSelected(this, isSelected);
	}

	@Override
	public void setTargetSelectionListener(TargetSelectionListener selectionListener) {
		this.selectionListener = selectionListener;
	}

	public interface TargetSelectionListener {
		void targetSelected(Target target, boolean isSelected);
	}

	public boolean isSelected() {
		return isSelected;
	}

	private void addResizeAnchors() {
		final Bounds localBounds = getTargetGroup().getBoundsInLocal();
		final double horizontalMiddle = localBounds.getMinX() + (localBounds.getWidth() / 2) - (ANCHOR_WIDTH / 2);
		final double verticleMiddle = localBounds.getMinY() + (localBounds.getHeight() / 2) - (ANCHOR_HEIGHT / 2);

		// Top left
		addAnchor(localBounds.getMinX(), localBounds.getMinY());
		// Top middle
		addAnchor(horizontalMiddle, localBounds.getMinY());
		// Top right
		addAnchor(localBounds.getMaxX() - ANCHOR_WIDTH, localBounds.getMinY());
		// Middle left
		addAnchor(localBounds.getMinX(), verticleMiddle);
		// Middle right
		addAnchor(localBounds.getMaxX() - ANCHOR_WIDTH, verticleMiddle);
		// Bottom left
		addAnchor(localBounds.getMinX(), localBounds.getMaxY() - ANCHOR_HEIGHT);
		// Bottom middle
		addAnchor(horizontalMiddle, localBounds.getMaxY() - ANCHOR_HEIGHT);
		// Bottom right
		addAnchor(localBounds.getMaxX() - ANCHOR_WIDTH, localBounds.getMaxY() - ANCHOR_HEIGHT);
	}

	private RectangleRegion addAnchor(final double x, final double y) {
		final RectangleRegion anchor = new RectangleRegion(x, y, ANCHOR_WIDTH, ANCHOR_HEIGHT);

		// Make the anchor regions unshootable and unresizable
		final Map<String, String> regionTags = ((TargetRegion) anchor).getAllTags();
		regionTags.put(TargetView.TAG_IGNORE_HIT, "true");
		regionTags.put(TargetView.TAG_RESIZABLE, "false");

		anchor.setFill(Color.GOLD);
		anchor.setStroke(Color.BLACK);

		getTargetGroup().getChildren().add(anchor);

		// Ensure anchors appear the intended visual size even if the target
		// has been scaled
		if (targetGroup.getScaleX() != 1.0f) {
			final double scaledPercentChange = (ANCHOR_WIDTH / (ANCHOR_WIDTH * targetGroup.getScaleX()));
			anchor.setScaleX(scaledPercentChange);
		}

		if (targetGroup.getScaleY() != 1.0f) {
			final double scaledPercentChange = (ANCHOR_HEIGHT / (ANCHOR_HEIGHT * targetGroup.getScaleY()));
			anchor.setScaleY(scaledPercentChange);
		}

		resizeAnchors.add(anchor);

		return anchor;
	}

	@Override
	public Optional<Hit> isHit(double x, double y) {
		if (targetGroup.getBoundsInParent().contains(x, y)) {
			// Target was hit, see if a specific region was hit
			for (int i = targetGroup.getChildren().size() - 1; i >= 0; i--) {
				final Node node = targetGroup.getChildren().get(i);

				if (!(node instanceof TargetRegion)) continue;

				final Bounds nodeBounds = targetGroup.getLocalToParentTransform().transform(node.getBoundsInParent());

				final int adjustedX = (int) (x - nodeBounds.getMinX());
				final int adjustedY = (int) (y - nodeBounds.getMinY());

				if (nodeBounds.contains(x, y)) {
					// If we hit an image region on a transparent pixel,
					// ignore it
					final TargetRegion region = (TargetRegion) node;

					// Ignore regions where ignoreHit tag is true
					if (region.tagExists(TargetView.TAG_IGNORE_HIT)
							&& Boolean.parseBoolean(region.getTag(TargetView.TAG_IGNORE_HIT)))
						continue;

					if (region.getType() == RegionType.IMAGE) {
						// The image you get from the image view is its
						// original size. We need to resize it if it has
						// changed size to accurately determine if a pixel
						// is transparent
						final Image currentImage = ((ImageRegion) region).getImage();

						if (adjustedX < 0 || adjustedY < 0) {
							logger.debug(
									"An adjusted pixel is negative: Adjusted ({}, {}), Original ({}, {}), "
											+ " nodeBounds.getMin ({}, {})",
									adjustedX, adjustedY, x, y, nodeBounds.getMaxX(),
									nodeBounds.getMinY());
							return Optional.empty();
						}

						if (Math.abs(currentImage.getWidth() - nodeBounds.getWidth()) > .0000001
								|| Math.abs(currentImage.getHeight() - nodeBounds.getHeight()) > .0000001) {

							final BufferedImage bufferedOriginal = SwingFXUtils.fromFXImage(currentImage, null);

							final java.awt.Image tmp = bufferedOriginal.getScaledInstance((int) nodeBounds.getWidth(),
									(int) nodeBounds.getHeight(), java.awt.Image.SCALE_SMOOTH);
							final BufferedImage bufferedResized = new BufferedImage((int) nodeBounds.getWidth(),
									(int) nodeBounds.getHeight(), BufferedImage.TYPE_INT_ARGB);

							final Graphics2D g2d = bufferedResized.createGraphics();
							g2d.drawImage(tmp, 0, 0, null);
							g2d.dispose();

							try {
								if (adjustedX >= bufferedResized.getWidth() || adjustedY >= bufferedResized.getHeight()
										|| bufferedResized.getRGB(adjustedX, adjustedY) >> 24 == 0) {
									continue;
								}
							} catch (final ArrayIndexOutOfBoundsException e) {
								final String message = String.format(
										"Index out of bounds while trying to find adjusted coordinate (%d, %d) "
												+ "from original (%.2f, %.2f) in adjusted BufferedImage for target %s "
												+ "with width = %d, height = %d",
										adjustedX, adjustedY, x, y, getTargetFile().getPath(),
										bufferedResized.getWidth(), bufferedResized.getHeight());
								logger.error(message, e);
								return Optional.empty();
							}
						} else {
							if (adjustedX >= currentImage.getWidth() || adjustedY >= currentImage.getHeight()
									|| currentImage.getPixelReader().getArgb(adjustedX, adjustedY) >> 24 == 0) {
								continue;
							}
						}
					} else {
						// The shot is in the bounding box but make sure it
						// is in the shape's
						// fill otherwise we can get a shot detected where
						// there isn't actually
						// a region showing
						final Point2D localCoords = targetGroup.parentToLocal(x, y);
						if (!node.contains(localCoords)) continue;
					}

					return Optional.of(new Hit(this, (TargetRegion) node, adjustedX, adjustedY));
				}
			}
		}

		return Optional.empty();
	}

	private void mousePressed() {
		targetGroup.setOnMousePressed((event) -> {
			if (!isInResizeZone(event)) {
				move = true;

				return;
			}

			resize = true;
			top = isTopZone(event);
			bottom = isBottomZone(event);
			left = isLeftZone(event);
			right = isRightZone(event);
		});
	}

	private void mouseDragged() {
		targetGroup.setOnMouseDragged((event) -> {

			if (!resize && !move) return;

			boolean fixedAspectRatioResize = false;
			double aspectScaleDelta = 0.0;

			if (move) {
				if (config.isPresent() && config.get().inDebugMode() && (event.isControlDown() || event.isShiftDown()))
					return;

				final double deltaX = event.getX() - x;
				final double deltaY = event.getY() - y;

				if (!keepInBounds || (targetGroup.getBoundsInParent().getMinX() + deltaX >= 0
						&& targetGroup.getBoundsInParent().getMaxX() + deltaX <= config.get().getDisplayWidth())) {

					targetGroup.setLayoutX(targetGroup.getLayoutX() + (deltaX * targetGroup.getScaleX()));
				}

				if (!keepInBounds || (targetGroup.getBoundsInParent().getMinY() + deltaY >= 0
						&& targetGroup.getBoundsInParent().getMaxY() + deltaY <= config.get().getDisplayHeight())) {

					targetGroup.setLayoutY(targetGroup.getLayoutY() + (deltaY * targetGroup.getScaleY()));
				}

				if (config.isPresent() && config.get().getSessionRecorder().isPresent()) {
					config.get().getSessionRecorder().get().recordTargetMoved(cameraName, this,
							(int) targetGroup.getLayoutX(), (int) targetGroup.getLayoutY());
				}

				return;
			}

			if ((top || bottom) && (left || right) && event.isControlDown()) fixedAspectRatioResize = true;

			if (left || right) {
				double gap; // The gap between the mouse and nearest
				// target edge

				if (right) {
					gap = (event.getX() - targetGroup.getLayoutBounds().getMaxX()) * targetGroup.getScaleX();
				} else {
					gap = (event.getX() - targetGroup.getLayoutBounds().getMinX()) * targetGroup.getScaleX();
				}

				final double currentWidth = targetGroup.getBoundsInParent().getWidth();
				final double newWidth = currentWidth + gap;

				double scaleDelta = (newWidth - currentWidth) / currentWidth;

				if (fixedAspectRatioResize) aspectScaleDelta = scaleDelta;

				final double currentOriginX = targetGroup.getBoundsInParent().getMinX();
				double newOriginX;

				if (right) {
					scaleDelta *= -1.0;
					newOriginX = currentOriginX - ((newWidth - currentWidth) / 2);
				} else {
					newOriginX = currentOriginX + ((newWidth - currentWidth) / 2);
				}

				double originXDelta = newOriginX - currentOriginX;

				if (right) originXDelta *= -1.0;

				final double oldLayoutX = targetGroup.getLayoutX();
				final double oldScaleX = targetGroup.getScaleX();
				final double newScaleX = oldScaleX * (1.0 - scaleDelta);

				// If we scale too small the target can do weird things
				if (newScaleX < 0.001 || Double.isNaN(newScaleX) || Double.isInfinite(newScaleX)) return;

				targetGroup.setLayoutX(targetGroup.getLayoutX() + originXDelta);
				targetGroup.setScaleX(newScaleX);

				if (keepInBounds && (targetGroup.getBoundsInParent().getMinX() <= 0
						|| targetGroup.getBoundsInParent().getMaxX() >= config.get().getDisplayWidth())) {

					// Target went out of bounds, so go back to the old size
					targetGroup.setLayoutX(oldLayoutX);
					targetGroup.setScaleX(oldScaleX);

				} else {
					// Target stayed in bounds so make sure that unresizable
					// target regions stay the same size
					for (final Node n : targetGroup.getChildren()) {
						if (!(n instanceof TargetRegion)) continue;

						final TargetRegion r = (TargetRegion) n;

						if (r.tagExists(Target.TAG_RESIZABLE)
								&& !Boolean.parseBoolean(r.getTag(Target.TAG_RESIZABLE))) {
							n.setScaleX(n.getScaleX() * (1.0 + scaleDelta));
						}
					}
				}
			}

			if (top || bottom) {
				double gap;

				if (bottom) {
					gap = (event.getY() - targetGroup.getLayoutBounds().getMaxY()) * targetGroup.getScaleY();
				} else {
					gap = (event.getY() - targetGroup.getLayoutBounds().getMinY()) * targetGroup.getScaleY();
				}

				final double currentHeight = targetGroup.getBoundsInParent().getHeight();
				double newHeight = currentHeight + gap;

				if (fixedAspectRatioResize) {
					if ((left && bottom) || (right && top)) aspectScaleDelta *= -1.0;

					newHeight = currentHeight + (currentHeight * aspectScaleDelta);
				}

				double scaleDelta = (newHeight - currentHeight) / currentHeight;

				final double currentOriginY = targetGroup.getBoundsInParent().getMinY();
				double newOriginY;

				if (bottom) {
					scaleDelta *= -1.0;
					newOriginY = currentOriginY - ((newHeight - currentHeight) / 2);
				} else {
					newOriginY = currentOriginY + ((newHeight - currentHeight) / 2);
				}

				double originYDelta = newOriginY - currentOriginY;

				if (bottom) originYDelta *= -1.0;

				final double oldLayoutY = targetGroup.getLayoutY();
				final double oldScaleY = targetGroup.getScaleY();
				final double newScaleY = oldScaleY * (1.0 - scaleDelta);

				// If we scale too small the target can do weird things
				if (newScaleY < 0.001 || Double.isNaN(newScaleY) || Double.isInfinite(newScaleY)) return;

				targetGroup.setLayoutY(targetGroup.getLayoutY() + originYDelta);
				targetGroup.setScaleY(newScaleY);

				if (keepInBounds && (targetGroup.getBoundsInParent().getMinY() <= 0
						|| targetGroup.getBoundsInParent().getMaxY() >= config.get().getDisplayHeight())) {

					// Target went out of bounds, so go back to the old size
					targetGroup.setLayoutY(oldLayoutY);
					targetGroup.setScaleY(oldScaleY);
				} else {
					// Target stayed in bounds so make sure that unresizable
					// target regions stay the same size
					for (final Node n : targetGroup.getChildren()) {
						if (!(n instanceof TargetRegion)) continue;

						final TargetRegion r = (TargetRegion) n;

						if (r.tagExists(Target.TAG_RESIZABLE)
								&& !Boolean.parseBoolean(r.getTag(Target.TAG_RESIZABLE))) {
							n.setScaleY(n.getScaleY() * (1.0 + scaleDelta));
						}
					}
				}
			}

			if (config.isPresent() && config.get().getSessionRecorder().isPresent()) {
				config.get().getSessionRecorder().get().recordTargetMoved(cameraName, this,
						(int) targetGroup.getLayoutX(), (int) targetGroup.getLayoutY());
			}

			if (config.isPresent() && config.get().getSessionRecorder().isPresent()) {
				config.get().getSessionRecorder().get().recordTargetResized(cameraName, this,
						targetGroup.getBoundsInParent().getWidth(), targetGroup.getBoundsInParent().getHeight());
			}
		});
	}

	private void mouseMoved() {
		targetGroup.setOnMouseMoved((event) -> {
			x = event.getX();
			y = event.getY();

			if (isTopZone(event) && isLeftZone(event)) {
				targetGroup.setCursor(Cursor.NW_RESIZE);
			} else if (isTopZone(event) && isRightZone(event)) {
				targetGroup.setCursor(Cursor.NE_RESIZE);
			} else if (isBottomZone(event) && isLeftZone(event)) {
				targetGroup.setCursor(Cursor.SW_RESIZE);
			} else if (isBottomZone(event) && isRightZone(event)) {
				targetGroup.setCursor(Cursor.SE_RESIZE);
			} else if (isTopZone(event)) {
				targetGroup.setCursor(Cursor.N_RESIZE);
			} else if (isBottomZone(event)) {
				targetGroup.setCursor(Cursor.S_RESIZE);
			} else if (isLeftZone(event)) {
				targetGroup.setCursor(Cursor.W_RESIZE);
			} else if (isRightZone(event)) {
				targetGroup.setCursor(Cursor.E_RESIZE);
			} else {
				targetGroup.setCursor(Cursor.DEFAULT);
			}
		});
	}

	private void mouseReleased() {
		targetGroup.setOnMouseReleased((event) -> {
			resize = false;
			move = false;
			targetGroup.setCursor(Cursor.DEFAULT);
		});
	}

	private void keyPressed() {
		targetGroup.setOnKeyPressed((event) -> {
			final double currentWidth = targetGroup.getBoundsInParent().getWidth();
			final double currentHeight = targetGroup.getBoundsInParent().getHeight();

			switch (event.getCode()) {
			case DELETE:
			case BACK_SPACE:
				if (userDeletable && parent.isPresent()) parent.get().removeTarget(this);
				break;

			case LEFT: {
				if (event.isShiftDown()) {
					final double newWidth = currentWidth - SCALE_DELTA;
					final double scaleDelta = (newWidth - currentWidth) / currentWidth;

					targetGroup.setScaleX(targetGroup.getScaleX() * (1.0 - scaleDelta));

					if (config.isPresent() && config.get().getSessionRecorder().isPresent()) {
						config.get().getSessionRecorder().get().recordTargetResized(cameraName, this,
								targetGroup.getBoundsInParent().getWidth(),
								targetGroup.getBoundsInParent().getHeight());
					}
				} else {
					if (!keepInBounds || (targetGroup.getBoundsInParent().getMinX() - MOVEMENT_DELTA >= 0
							&& targetGroup.getBoundsInParent().getMaxX() - MOVEMENT_DELTA <= config.get()
									.getDisplayWidth())) {

						targetGroup.setLayoutX(targetGroup.getLayoutX() - MOVEMENT_DELTA);
					}

					if (config.isPresent() && config.get().getSessionRecorder().isPresent()) {
						config.get().getSessionRecorder().get().recordTargetMoved(cameraName, this,
								(int) targetGroup.getLayoutX(), (int) targetGroup.getLayoutY());
					}
				}
			}

				break;

			case RIGHT: {
				if (event.isShiftDown()) {
					final double newWidth = currentWidth + SCALE_DELTA;
					final double scaleDelta = (newWidth - currentWidth) / currentWidth;

					if (!keepInBounds || (targetGroup.getBoundsInParent().getMinX() + (SCALE_DELTA / 2) >= 0
							&& targetGroup.getBoundsInParent().getMaxX() + (SCALE_DELTA / 2) <= config.get()
									.getDisplayWidth())) {
						targetGroup.setScaleX(targetGroup.getScaleX() * (1.0 - scaleDelta));
					}

					if (config.isPresent() && config.get().getSessionRecorder().isPresent()) {
						config.get().getSessionRecorder().get().recordTargetResized(cameraName, this,
								targetGroup.getBoundsInParent().getWidth(),
								targetGroup.getBoundsInParent().getHeight());
					}
				} else {
					if (!keepInBounds || (targetGroup.getBoundsInParent().getMinX() + MOVEMENT_DELTA >= 0
							&& targetGroup.getBoundsInParent().getMaxX() + MOVEMENT_DELTA <= config.get()
									.getDisplayWidth())) {

						targetGroup.setLayoutX(targetGroup.getLayoutX() + MOVEMENT_DELTA);
					}

					if (config.isPresent() && config.get().getSessionRecorder().isPresent()) {
						config.get().getSessionRecorder().get().recordTargetMoved(cameraName, this,
								(int) targetGroup.getLayoutX(), (int) targetGroup.getLayoutY());
					}
				}
			}

				break;

			case UP: {
				if (event.isShiftDown()) {
					final double newHeight = currentHeight - SCALE_DELTA;
					final double scaleDelta = (newHeight - currentHeight) / currentHeight;

					targetGroup.setScaleY(targetGroup.getScaleY() * (1.0 - scaleDelta));

					// Scale up proportionally if ctrl is down
					if (event.isControlDown()) {
						final double newWidth = currentWidth - (SCALE_DELTA * (currentWidth / currentHeight));
						final double widthDelta = (newWidth - currentWidth) / currentWidth;

						targetGroup.setScaleX(targetGroup.getScaleX() * (1.0 - widthDelta));
					}

					if (config.isPresent() && config.get().getSessionRecorder().isPresent()) {
						config.get().getSessionRecorder().get().recordTargetResized(cameraName, this,
								targetGroup.getBoundsInParent().getWidth(),
								targetGroup.getBoundsInParent().getHeight());
					}
				} else {
					if (!keepInBounds || (targetGroup.getBoundsInParent().getMinY() - MOVEMENT_DELTA >= 0
							&& targetGroup.getBoundsInParent().getMaxY() - MOVEMENT_DELTA <= config.get()
									.getDisplayHeight())) {

						targetGroup.setLayoutY(targetGroup.getLayoutY() - MOVEMENT_DELTA);
					}

					if (config.isPresent() && config.get().getSessionRecorder().isPresent()) {
						config.get().getSessionRecorder().get().recordTargetMoved(cameraName, this,
								(int) targetGroup.getLayoutX(), (int) targetGroup.getLayoutY());
					}
				}
			}

				break;

			case DOWN: {
				if (event.isShiftDown()) {
					final double newHeight = currentHeight + SCALE_DELTA;
					final double scaleDelta = (newHeight - currentHeight) / currentHeight;

					if (!keepInBounds || (targetGroup.getBoundsInParent().getMinY() + (SCALE_DELTA / 2) >= 0
							&& targetGroup.getBoundsInParent().getMaxY() + (SCALE_DELTA / 2) <= config.get()
									.getDisplayHeight())) {
						targetGroup.setScaleY(targetGroup.getScaleY() * (1.0 - scaleDelta));

						// Scale down proportionally if ctrl is down
						if (event.isControlDown()) {
							final double newWidth = currentWidth + (SCALE_DELTA * (currentWidth / currentHeight));
							final double widthDelta = (newWidth - currentWidth) / currentWidth;

							targetGroup.setScaleX(targetGroup.getScaleX() * (1.0 - widthDelta));
						}
					}

					if (config.isPresent() && config.get().getSessionRecorder().isPresent()) {
						config.get().getSessionRecorder().get().recordTargetResized(cameraName, this,
								targetGroup.getBoundsInParent().getWidth(),
								targetGroup.getBoundsInParent().getHeight());
					}
				} else {
					if (!keepInBounds || (targetGroup.getBoundsInParent().getMinY() + MOVEMENT_DELTA >= 0
							&& targetGroup.getBoundsInParent().getMaxY() + MOVEMENT_DELTA <= config.get()
									.getDisplayHeight())) {

						targetGroup.setLayoutY(targetGroup.getLayoutY() + MOVEMENT_DELTA);
					}

					if (config.isPresent() && config.get().getSessionRecorder().isPresent()) {
						config.get().getSessionRecorder().get().recordTargetMoved(cameraName, this,
								(int) targetGroup.getLayoutX(), (int) targetGroup.getLayoutY());
					}
				}
			}

				break;

			default:
				break;
			}
			event.consume();
		});
	}

	private boolean isTopZone(MouseEvent event) {
		return event.getY() < (targetGroup.getLayoutBounds().getMinY() + RESIZE_MARGIN);
	}

	private boolean isBottomZone(MouseEvent event) {
		return event.getY() > (targetGroup.getLayoutBounds().getMaxY() - RESIZE_MARGIN);
	}

	private boolean isLeftZone(MouseEvent event) {
		return event.getX() < (targetGroup.getLayoutBounds().getMinX() + RESIZE_MARGIN);
	}

	private boolean isRightZone(MouseEvent event) {
		return event.getX() > (targetGroup.getLayoutBounds().getMaxX() - RESIZE_MARGIN);
	}

	private boolean isInResizeZone(MouseEvent event) {
		return isTopZone(event) || isBottomZone(event) || isLeftZone(event) || isRightZone(event);
	}

	@Override
	public boolean tagExists(String name) {
		return targetTags.containsKey(name);
	}

	@Override
	public String getTag(String name) {
		return targetTags.get(name);
	}

	@Override
	public Map<String, String> getAllTags() {
		return targetTags;
	}
}