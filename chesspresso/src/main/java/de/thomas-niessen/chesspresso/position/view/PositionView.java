/*******************************************************************************
 * Basic version: Copyright (C) 2003 Bernhard Seybold. All rights reserved.
 * All changes since then: Copyright (C) Thomas Niessen. All rights reserved.
 * 
 * This library is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2.1 of the License, or (at
 * your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser
 * General Public License for more details.
 ******************************************************************************/
package chesspresso.position.view;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.TexturePaint;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.GeneralPath;
import java.awt.geom.PathIterator;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import chesspresso.Chess;
import chesspresso.Mouse;
import chesspresso.ScreenShot;
import chesspresso.game.view.UserAction;
import chesspresso.move.Move;
import chesspresso.position.Position;
import chesspresso.position.PositionListener;
import chesspresso.position.PositionMotionListener;
import chesspresso.position.view.Decoration.DecorationType;

/**
 * Position view.
 * 
 * @author Bernhard Seybold, Thomas Niessen
 */
@SuppressWarnings("serial")
public class PositionView extends JPanel implements PositionListener, MouseListener, MouseMotionListener, ScreenShot {
	private Position position;
	private int bottomPlayer;
	private boolean showCoordinates;
	private boolean decorationsEnabled; // restricts Chessbase decorations within the local MouseListener only
	private UserAction userAction;
	private Color whiteSquareColor;
	private Color blackSquareColor;
	private Color whiteColor;
	private Color blackColor;
	private boolean solidStones;

	private int draggedFrom;
	private int draggedStone;
	private int draggedX, draggedY;
	private PositionMotionListener positionMotionListener;

	private static final Color WHITE_SQUARE_DEFAULT_COLOR = new Color(232, 219, 200);
	private static final Color BLACK_SQUARE_DEFAULT_COLOR = new Color(224, 175, 100);

	private static final BufferedImage HIGHLIGHT_WHITE_DEFAULT;
	private static final BufferedImage HIGHLIGHT_BLACK_DEFAULT;

	static {
		{
			HIGHLIGHT_WHITE_DEFAULT = new BufferedImage(5, 5, BufferedImage.TYPE_INT_RGB);
			Graphics2D g2d = HIGHLIGHT_WHITE_DEFAULT.createGraphics();
			g2d.setColor(BLACK_SQUARE_DEFAULT_COLOR);
			g2d.fillRect(0, 0, 5, 5);
			g2d.setColor(WHITE_SQUARE_DEFAULT_COLOR);
			g2d.fillOval(0, 0, 5, 5);
		}
		{
			HIGHLIGHT_BLACK_DEFAULT = new BufferedImage(5, 5, BufferedImage.TYPE_INT_RGB);
			Graphics2D g2d = HIGHLIGHT_BLACK_DEFAULT.createGraphics();
			g2d.setColor(WHITE_SQUARE_DEFAULT_COLOR);
			g2d.fillRect(0, 0, 5, 5);
			g2d.setColor(BLACK_SQUARE_DEFAULT_COLOR);
			g2d.fillOval(0, 0, 5, 5);
		}
	}

	private static final Paint HIGHLIGHT_PAINT_WHITE = new TexturePaint(HIGHLIGHT_WHITE_DEFAULT, new Rectangle(0, 0, 5, 5));
	private static final Paint HIGHLIGHT_PAINT_BLACK = new TexturePaint(HIGHLIGHT_BLACK_DEFAULT, new Rectangle(0, 0, 5, 5));

	private static final int SQUARE_OFFSET = 4;

	final private Map<Integer, Paint> backgroundPaints = new HashMap<>();

	final private Object decorationToken = new Object();
	final private List<Decoration> lowerLevel = new ArrayList<>(); // below the figure symbols
	final private List<Decoration> upperLevel = new ArrayList<>(); // above the figure symbols

	private static final Color GREEN_TRANSPARENT = new Color(0.f, 1.f, 0.f, 0.6f);
	private static final Color YELLOW_TRANSPARENT = new Color(1.f, 1.f, 0.f, 0.6f);
	private static final Color RED_TRANSPARENT = new Color(1.f, 0.f, 0.f, 0.6f);

	private static final String DEFAULT_FONT_NAME = "CS Chess Merida Unicode";
	private static final int DEFAULT_FONT_SIZE = 32;

	private PieceTracker pieceTracker = null;

	// ======================================================================

	/**
	 * Create a new position view.
	 * 
	 * @param position the position to display
	 */
	public PositionView(Position position) {
		this(position, Chess.WHITE, UserAction.ENABLED);
	}

	/**
	 * Creates a new position view with default font size.
	 * 
	 * @param position     the position to display
	 * @param bottomPlayer the player at the lower edge
	 * @param userAction   user action
	 */
	public PositionView(Position position, int bottomPlayer, UserAction userAction) {
		this(position, bottomPlayer, userAction, DEFAULT_FONT_SIZE);
	}

	/**
	 * Create a new position view.
	 * 
	 * @param position     the position to display
	 * @param bottomPlayer the player at the lower edge
	 * @param userAction   user action
	 * @param fontSize     the font size
	 */
	public PositionView(Position position, int bottomPlayer, UserAction userAction, int fontSize) {
		this.position = position;
		this.bottomPlayer = bottomPlayer;
		showCoordinates = false;
		decorationsEnabled = false;
		this.userAction = userAction;
		whiteSquareColor = WHITE_SQUARE_DEFAULT_COLOR;
		blackSquareColor = BLACK_SQUARE_DEFAULT_COLOR;
		whiteColor = Color.BLACK;
		blackColor = Color.BLACK;
		solidStones = true;

		setFont(new Font(DEFAULT_FONT_NAME, Font.PLAIN, fontSize));
		// If this font is not available, Java's logical font "Dialog" will be chosen.

		draggedStone = Chess.NO_STONE;
		draggedFrom = Chess.NO_SQUARE;
		positionMotionListener = null;
		position.addPositionListener(this); // Seybold: when do we remove it?
		addMouseListener(this);
		addMouseMotionListener(this);
	}

	// ======================================================================
	public void setPosition(Position position) {
		this.position.removePositionListener(this);
		this.position = position;
		this.position.addPositionListener(this);
		removeAllPieceTracking(true);
	}

	// ======================================================================
	public void setImmutablePosition(Position position) {
		this.position = position;
	}

	// ======================================================================

	public int getBottomPlayer() {
		return bottomPlayer;
	}

	public void setBottomPlayer(int player) {
		if (player != bottomPlayer) {
			bottomPlayer = player;
			repaint();
		}
	}

	public void setShowCoordinates(boolean showCoordinates) {
		this.showCoordinates = showCoordinates;
	}

	public void setDecorationsEnabled(boolean enable) {
		decorationsEnabled = enable;
	}

	public void setUserAction(UserAction userAction) {
		this.userAction = userAction;
	}

	public Color getWhiteSquareColor() {
		return whiteSquareColor;
	}

	public Color getBlackSquareColor() {
		return blackSquareColor;
	}

	public Color getWhiteColor() {
		return whiteColor;
	}

	public Color getBlackColor() {
		return blackColor;
	}

	public boolean getSolidStones() {
		return solidStones;
	}

	public void setWhiteSquareColor(Color color) {
		if (whiteSquareColor == color) {
			return;
		}
		whiteSquareColor = color;
		repaint();
	}

	public void setBlackSquareColor(Color color) {
		if (blackSquareColor == color) {
			return;
		}
		blackSquareColor = color;
		repaint();
	}

	public void setWhiteSquareColorToDefault() {
		whiteSquareColor = WHITE_SQUARE_DEFAULT_COLOR;
		repaint();
	}

	public void setBlackSquareColorToDefault() {
		blackSquareColor = BLACK_SQUARE_DEFAULT_COLOR;
		repaint();
	}

	public void setWhiteColor(Color color) {
		if (whiteColor == color) {
			return;
		}
		whiteColor = color;
		repaint();
	}

	public void setBlackColor(Color color) {
		if (blackColor == color) {
			return;
		}
		blackColor = color;
		repaint();
	}

	public void setProperties(PositionViewProperties props) {
		PositionView view = props.getPositionView();
		setWhiteColor(view.getWhiteColor());
		setBlackColor(view.getBlackColor());
		setWhiteSquareColor(view.getWhiteSquareColor());
		setBlackSquareColor(view.getBlackSquareColor());
		setFont(view.getFont());
	}

	@Override
	public void setFont(Font font) {
		super.setFont(font);
		repaint();
	}

	public void setSolidStones(boolean solid) {
		if (solid == solidStones) {
			return;
		}
		solidStones = solid;
		repaint();
	}

	/**
	 * Flip board.
	 */
	public void flip() {
		setBottomPlayer(Chess.otherPlayer(bottomPlayer));
	}

	public Position getPosition() {
		return position;
	}

	@Override
	public Dimension getPreferredSize() {
		return new Dimension(Chess.NUM_OF_COLS * (getFont().getSize() + SQUARE_OFFSET),
				Chess.NUM_OF_ROWS * (getFont().getSize() + SQUARE_OFFSET));
	}

	@Override
	public Dimension getMinimumSize() {
		return new Dimension(Chess.NUM_OF_COLS * (getFont().getSize() + SQUARE_OFFSET),
				Chess.NUM_OF_ROWS * (getFont().getSize() + SQUARE_OFFSET));
	}

	@Override
	public Dimension getMaximumSize() {
		return new Dimension(Chess.NUM_OF_COLS * (getFont().getSize() + SQUARE_OFFSET),
				Chess.NUM_OF_ROWS * (getFont().getSize() + SQUARE_OFFSET));
	}

	public int getSquareSize() {
		return getFont().getSize() + SQUARE_OFFSET;
	}

	public int getSquare(int x, int y) {
		if (x < 0 || y < 0) {
			return -1;
		}
		int squareSize = getSquareSize();
		if (x >= Chess.NUM_OF_COLS * squareSize || y >= Chess.NUM_OF_ROWS * squareSize) {
			return -1;
		}

		int y0 = y / squareSize;
		int x0 = x / squareSize;
		if (bottomPlayer == Chess.WHITE) {
			return Chess.NUM_OF_COLS * (Chess.NUM_OF_ROWS - 1 - y0) + x0;
		} else {
			return Chess.NUM_OF_COLS * y0 + Chess.NUM_OF_COLS - 1 - x0;
		}
	}

	public void setOrRemovePaint(int sqi, Paint paint) {
		if (paint == null) {
			backgroundPaints.remove(sqi);
		} else {
			backgroundPaints.put(sqi, paint);
		}
		repaint();
	}

	public void setHighlight(int sqi, boolean highlight) {
		if (highlight) {
			if (Chess.isWhiteSquare(sqi)) {
				backgroundPaints.put(sqi, HIGHLIGHT_PAINT_WHITE);
			} else {
				backgroundPaints.put(sqi, HIGHLIGHT_PAINT_BLACK);
			}
		} else {
			backgroundPaints.remove(sqi);
		}
		repaint();
	}

	public void toggleHighlight(int sqi) {
		if (backgroundPaints.containsKey(sqi)) {
			backgroundPaints.remove(sqi);
		} else {
			if (Chess.isWhiteSquare(sqi)) {
				backgroundPaints.put(sqi, HIGHLIGHT_PAINT_WHITE);
			} else {
				backgroundPaints.put(sqi, HIGHLIGHT_PAINT_BLACK);
			}
		}
		repaint();
	}

	public void removeAllHighlighting() {
		for (int sqi = Chess.A1; sqi <= Chess.H8; ++sqi) {
			Paint paint = backgroundPaints.get(sqi);
			if (paint != null) {
				if (paint == HIGHLIGHT_PAINT_WHITE || paint == HIGHLIGHT_PAINT_BLACK) {
					backgroundPaints.remove(sqi);
				}
			}
		}
		repaint();
	}

	public void addDecoration(Decoration decoration, boolean onTop) {
		if (decoration != null) {
			synchronized (decorationToken) {
				if (onTop) {
					upperLevel.add(decoration);
				} else {
					lowerLevel.add(decoration);
				}
			}
			repaint();
		}
	}

	public void removeAllDecorations() {
		synchronized (decorationToken) {
			lowerLevel.clear();
			upperLevel.clear();
		}
		repaint();
	}

	public void removeDecorations(Decoration.DecorationType type, Color color, Object owner) {
		if (type != null) {
			synchronized (decorationToken) {
				if (owner == null) {
					if (color != null) {
						lowerLevel.removeIf(d -> d.getType().equals(type) && d.getColor().equals(color));
						upperLevel.removeIf(d -> d.getType().equals(type) && d.getColor().equals(color));
					} else {
						try {
							lowerLevel.removeIf(d -> d.getType().equals(type));
							upperLevel.removeIf(d -> d.getType().equals(type));
						} catch (NullPointerException ex) {
							ex.printStackTrace();
						}
					}
				} else { // owner != null 
					if (color != null) {
						lowerLevel.removeIf(
								d -> d.getType().equals(type) && d.getColor().equals(color) && d.getOwner().equals(owner));
						upperLevel.removeIf(
								d -> d.getType().equals(type) && d.getColor().equals(color) && d.getOwner().equals(owner));
					} else {
						try {
							lowerLevel.removeIf(d -> d.getType().equals(type) && d.getOwner().equals(owner));
							upperLevel.removeIf(d -> d.getType().equals(type) && d.getOwner().equals(owner));
						} catch (NullPointerException ex) {
							ex.printStackTrace();
						}
					}
				}
			}
			repaint();
		}
	}

	public void removeDecorations(Decoration.DecorationType type, Color color, Object owner, Predicate<Decoration> predicate) {
		if (type != null) {
			synchronized (decorationToken) {
				if (color != null) {
					lowerLevel.removeIf(d -> d.getType().equals(type) && d.getColor().equals(color) && predicate.test(d));
					upperLevel.removeIf(d -> d.getType().equals(type) && d.getColor().equals(color) && predicate.test(d));
				} else {
					try {
						lowerLevel.removeIf(d -> d.getType().equals(type) && predicate.test(d));
						upperLevel.removeIf(d -> d.getType().equals(type) && predicate.test(d));
					} catch (NullPointerException ex) {
						ex.printStackTrace();
					}
				}
			}
			repaint();
		}
	}

	public void highlightLastMove(Move lastMove, Object owner, Color color) {
		removeDecorations(DecorationType.ARROW, color, owner);
		if (lastMove != null && lastMove.getShortMoveDesc() != Move.NULL_MOVE) {
			if (!lastMove.isCastle() && !lastMove.isCastleChess960()) {
				addDecoration(DecorationFactory.getArrowDecoration(lastMove.getFromSqi(), lastMove.getToSqi(), color, owner),
						false);
			} else {
				int fromSquare, toSquare;
				if (lastMove.isWhiteMove()) {
					if (lastMove.isShortCastle() || lastMove.isShortCastleChess960()) {
						fromSquare = Chess.F1;
						toSquare = Chess.G1;
					} else {
						fromSquare = Chess.C1;
						toSquare = Chess.D1;
					}
				} else {
					if (lastMove.isShortCastle() || lastMove.isShortCastleChess960()) {
						fromSquare = Chess.F8;
						toSquare = Chess.G8;
					} else {
						fromSquare = Chess.C8;
						toSquare = Chess.D8;
					}
				}
				addDecoration(DecorationFactory.getArrowDecoration(fromSquare, toSquare, color, owner), false);
				addDecoration(DecorationFactory.getArrowDecoration(toSquare, fromSquare, color, owner), false);
			}
		}
		repaint();
	}

	// ======================================================================
	// PieceTracker

	/*
	 * A PieceTracker visualizes on a PositionView, but it is based on a game.
	 * So, it is natural that it has to be set from outside. 
	 */

	public void setPieceTracker(PieceTracker pieceTracker) {
		this.pieceTracker = pieceTracker;
		repaint();
	}

	public void addToPieceTracking(int sqi) {
		if (pieceTracker != null) {
			pieceTracker.addPiece(sqi);
			updatePieceTracking();
		}
	}

	public void removeFromPieceTracking(int sqi) {
		if (pieceTracker != null) {
			pieceTracker.removePiece(sqi);
			updatePieceTracking();
		}
	}

	public void removeAllPieceTracking(boolean removeTrackedPieces) {
		if (pieceTracker != null) {
			if (removeTrackedPieces) {
				pieceTracker.removeAllPieces();
			}
			removeDecorations(DecorationType.ARROW, null, pieceTracker);
			repaint();
		}
	}

	public void updatePieceTracking() {
		if (pieceTracker != null) {
			pieceTracker.updateDecorations(this);
			repaint();
		}
	}

	// ======================================================================
	// PositionListener

	@Override
	public void positionChanged(ChangeType type, short move, String fen) {
		if (type.equals(ChangeType.START_POS_CHANGED)) {
			removeAllPieceTracking(true);
		}
		updatePieceTracking();
		repaint();
	}

	// ======================================================================

	public void setPositionMotionListener(PositionMotionListener listener) {
		positionMotionListener = listener;
	}

	@Override
	public void mouseClicked(MouseEvent e) {
	}

	@Override
	public void mouseEntered(MouseEvent e) {
	}

	@Override
	public void mouseExited(MouseEvent e) {
	}

	@Override
	public void mousePressed(MouseEvent e) {
		if (userAction == UserAction.DISABLED) {
			return;
		}
		if (Mouse.isSpecial(e) || SwingUtilities.isRightMouseButton(e)) {
			if (e.isAltDown()) {
				draggedFrom = getSquare(e.getX(), e.getY());
			}
			return;
		}
		if (positionMotionListener == null) {
			return;
		}
		draggedFrom = getSquare(e.getX(), e.getY());
		if (positionMotionListener.isDragAllowed(position, draggedFrom)) {
			draggedStone = position.getStone(draggedFrom);
			draggedX = e.getX();
			draggedY = e.getY();
			repaint();
		} else {
			positionMotionListener.squareClicked(position, draggedFrom, e);
			draggedFrom = Chess.NO_SQUARE;
		}
	}

	private void drawChessbaseDecorations(MouseEvent e) {
		if (decorationsEnabled) {
			if (e.isAltDown() && draggedFrom != Chess.NO_SQUARE) {
				int draggedTo = getSquare(e.getX(), e.getY());
				if (draggedTo == Chess.NO_SQUARE) {
					return;
				} else if (draggedTo == draggedFrom) {
					if (e.isControlDown() || e.isMetaDown()) {
						setOrRemovePaint(draggedTo, YELLOW_TRANSPARENT);
					} else if (e.isShiftDown()) {
						setOrRemovePaint(draggedTo, RED_TRANSPARENT);
					} else {
						setOrRemovePaint(draggedTo, GREEN_TRANSPARENT);
					}
				} else { // arrows
					if (e.isControlDown() || e.isMetaDown()) {
						addDecoration(DecorationFactory.getArrowDecoration(draggedFrom, draggedTo, YELLOW_TRANSPARENT,
								PositionView.this), true);
					} else if (e.isShiftDown()) {
						addDecoration(
								DecorationFactory.getArrowDecoration(draggedFrom, draggedTo, RED_TRANSPARENT, PositionView.this),
								true);
					} else {
						addDecoration(DecorationFactory.getArrowDecoration(draggedFrom, draggedTo, GREEN_TRANSPARENT,
								PositionView.this), true);
					}
				}
			}
			draggedFrom = Chess.NO_SQUARE; // otherwise paint draws there an empty square
			repaint();
		}
	}

	public void removeChessbaseDecorations() {
		backgroundPaints.values()
				.removeIf(p -> p.equals(YELLOW_TRANSPARENT) || p.equals(RED_TRANSPARENT) || p.equals(GREEN_TRANSPARENT));
		removeDecorations(DecorationType.ARROW, YELLOW_TRANSPARENT, PositionView.this);
		removeDecorations(DecorationType.ARROW, RED_TRANSPARENT, PositionView.this);
		removeDecorations(DecorationType.ARROW, GREEN_TRANSPARENT, PositionView.this);
	}

	@Override
	public void mouseReleased(MouseEvent e) {
		if (userAction == UserAction.DISABLED) {
			return;
		}
		if (Mouse.isSpecial(e)) {
			if (e.isAltDown()) {
				drawChessbaseDecorations(e);
			}
			if (draggedStone != Chess.NO_STONE) {
				draggedFrom = Chess.NO_SQUARE;
				draggedStone = Chess.NO_STONE;
				repaint();
			}
			return;
		}
		if (positionMotionListener == null || userAction == UserAction.NAVIGABLE) {
			return;
		}
		if (draggedFrom != Chess.NO_SQUARE) {
			int draggedTo = getSquare(e.getX(), e.getY());
			if (draggedTo != Chess.NO_SQUARE) {
				if (draggedFrom == draggedTo) {
					positionMotionListener.squareClicked(position, draggedFrom, e);
				} else {
					positionMotionListener.dragged(position, draggedFrom, draggedTo, e);
				}
			}
			draggedFrom = Chess.NO_SQUARE;
			draggedStone = Chess.NO_STONE;
			repaint();
		}
	}

	@Override
	public void mouseDragged(MouseEvent e) {
		if (userAction == UserAction.DISABLED) {
			return;
		}
		if (Mouse.isSpecial(e)) {
			if (draggedStone != Chess.NO_STONE) {
				draggedFrom = Chess.NO_SQUARE;
				draggedStone = Chess.NO_STONE;
				repaint();
			}
			return;
		}
		if (draggedFrom != Chess.NO_SQUARE) {
			draggedX = e.getX();
			draggedY = e.getY();
			repaint();
		}
	}

	@Override
	public void mouseMoved(MouseEvent e) {
	}

	private final static String[] WHITE_STONE_STRINGS = { "\u2654", "\u2659", "\u2655", "\u2656", "\u2657", "\u2658", " ",
			"\u2658", "\u2657", "\u2656", "\u2655", "\u2659", "\u2654" },
			BLACK_STONE_STRINGS = { "\u265A", "\u265F", "\u265B", "\u265C", "\u265D", "\u265E", " ", "\u265E", "\u265D", "\u265C",
					"\u265B", "\u265F", "\u265A" };

	private static String getStringForStone(int stone, boolean isWhite) {
		return isWhite ? WHITE_STONE_STRINGS[stone - Chess.MIN_STONE] : BLACK_STONE_STRINGS[stone - Chess.MIN_STONE];
	}

	public static String getStringForStone(int stone) {
		return stone < 0 ? WHITE_STONE_STRINGS[stone - Chess.MIN_STONE] : BLACK_STONE_STRINGS[stone - Chess.MIN_STONE];
	}

	@Override
	public void paintComponent(Graphics graphics) {
		super.paintComponent(graphics);
		Graphics2D g2 = (Graphics2D) graphics;
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		int squareSize = getFont().getSize() + SQUARE_OFFSET;

		// First step: draw the background.
		for (int y = 0; y < Chess.NUM_OF_ROWS; y++) {
			for (int x = 0; x < Chess.NUM_OF_COLS; x++) {
				int sqi = (bottomPlayer == Chess.WHITE ? Chess.coorToSqi(x, Chess.NUM_OF_ROWS - y - 1)
						: Chess.coorToSqi(Chess.NUM_OF_COLS - x - 1, y));
				if (backgroundPaints.containsKey(sqi)) {
					g2.setPaint(backgroundPaints.get(sqi));
				} else if (Chess.isWhiteSquare(sqi)) {
					graphics.setColor(whiteSquareColor);
				} else {
					graphics.setColor(blackSquareColor);
				}
				graphics.fillRect(x * squareSize, y * squareSize, squareSize, squareSize);
			}
		}

		// Second step: draw letters and numbers, if needed.
		if (showCoordinates) {
			drawCoordinates(g2);
		}

		// Third step: draw the layer below the stones.
		synchronized (decorationToken) {
			for (Decoration decoration : lowerLevel) {
				decoration.paint(g2, squareSize, bottomPlayer);
			}
		}

		// Fourth step: get the stone and paint over the background with white color.
		for (int y = 0; y < Chess.NUM_OF_ROWS; y++) {
			for (int x = 0; x < Chess.NUM_OF_COLS; x++) {
				int sqi = (bottomPlayer == Chess.WHITE ? Chess.coorToSqi(x, Chess.NUM_OF_ROWS - y - 1)
						: Chess.coorToSqi(Chess.NUM_OF_COLS - x - 1, y));
				int stone = (sqi == draggedFrom ? Chess.NO_STONE : position.getStone(sqi));
				boolean stoneIsWhite = Chess.stoneToColor(stone) == Chess.WHITE;
				graphics.setColor(stoneIsWhite ? whiteColor : blackColor);
				if (solidStones) {
					stone = Chess.pieceToStone(Chess.stoneToPiece(stone), Chess.BLACK);
				}
				FontRenderContext frc = g2.getFontRenderContext();
				GlyphVector gv = getFont().createGlyphVector(frc, getStringForStone(stone, stoneIsWhite));

				final Shape shape = gv.getOutline();
				final Rectangle r = shape.getBounds();
				final int spaceX = squareSize - r.width;
				final int spaceY = squareSize - r.height;
				int transX = x * squareSize - r.x + (spaceX / 2);
				int transY = y * squareSize - r.y + (spaceY / 2);
				final AffineTransform trans = AffineTransform.getTranslateInstance(transX, transY);
				final Shape shapeCentered = trans.createTransformedShape(shape);
				final Area shapeArea = new Area(shapeCentered);
				Color color = g2.getColor();
				g2.setColor(Color.WHITE);
				final ArrayList<Shape> partsOfShape = getAllPartsOfTheShape(shapeArea);
				for (final Shape part : partsOfShape) {
					g2.fill(part);
				}
				// draw the stone's image
				g2.setColor(color);
				graphics.drawString(getStringForStone(stone, stoneIsWhite), transX, transY);
			}
		}

		// Fifth step: draw the layer above the stones.
		synchronized (decorationToken) {
			for (Decoration decoration : upperLevel) {
				decoration.paint(g2, squareSize, bottomPlayer);
			}
		}

		// Drag & Drop
		if (draggedStone != Chess.NO_STONE) {
			// That's the old implementation. The first line does nothing, because the two
			// colors are equal.
			//			graphics.setColor(Chess.stoneToColor(draggedStone) == Chess.WHITE ? whiteColor : blackColor);
			//			graphics.drawString(getStringForStone(draggedStone, true), draggedX - size / 2, draggedY + size / 2);
			// End of old implementation.
			// NEW: Again first the background
			FontRenderContext frc = g2.getFontRenderContext();
			GlyphVector gv = getFont().createGlyphVector(frc,
					getStringForStone(draggedStone, Chess.stoneToColor(draggedStone) == Chess.WHITE));

			final Shape shape = gv.getOutline();
			int transX = draggedX - squareSize / 2;
			int transY = draggedY + squareSize / 2;
			final AffineTransform trans = AffineTransform.getTranslateInstance(transX, transY);
			final Shape shapeCentered = trans.createTransformedShape(shape);
			final Area shapeArea = new Area(shapeCentered);
			g2.setColor(Color.WHITE);
			final ArrayList<Shape> partsOfShape = getAllPartsOfTheShape(shapeArea);
			for (final Shape part : partsOfShape) {
				g2.fill(part);
			}
			// and then the piece itself
			g2.setColor(Chess.stoneToColor(draggedStone) == Chess.WHITE ? whiteColor : blackColor);
			graphics.drawString(getStringForStone(draggedStone, Chess.stoneToColor(draggedStone) == Chess.WHITE),
					draggedX - squareSize / 2, draggedY + squareSize / 2);
		}
	}

	public static ArrayList<Shape> getAllPartsOfTheShape(final Shape shape) {
		final ArrayList<Shape> partsOfShape = new ArrayList<>();

		GeneralPath generalPath = new GeneralPath();
		for (final PathIterator pathIterater = shape.getPathIterator(null); !pathIterater.isDone(); pathIterater.next()) {
			final double[] coords = new double[6];
			final int pathSegmentType = pathIterater.currentSegment(coords);
			final int windingRule = pathIterater.getWindingRule();
			generalPath.setWindingRule(windingRule);
			switch (pathSegmentType) {
			case PathIterator.SEG_MOVETO -> {
				generalPath = new GeneralPath();
				generalPath.setWindingRule(windingRule);
				generalPath.moveTo(coords[0], coords[1]);
			}
			case PathIterator.SEG_LINETO -> generalPath.lineTo(coords[0], coords[1]);
			case PathIterator.SEG_QUADTO -> generalPath.quadTo(coords[0], coords[1], coords[2], coords[3]);
			case PathIterator.SEG_CUBICTO -> generalPath.curveTo(coords[0], coords[1], coords[2], coords[3], coords[4],
					coords[5]);
			case PathIterator.SEG_CLOSE -> {
				generalPath.closePath();
				partsOfShape.add(new Area(generalPath));
			}
			default -> {
			}
			}
		}

		return partsOfShape;
	}

	private final static char[][] LETTERS = { { 'a' }, { 'b' }, { 'c' }, { 'd' }, { 'e' }, { 'f' }, { 'g' }, { 'h' } };
	private final static char[][] NUMBERS = { { '1' }, { '2' }, { '3' }, { '4' }, { '5' }, { '6' }, { '7' }, { '8' } };

	private void drawCoordinates(Graphics2D g2) {
		Font oldFont = g2.getFont();
		Font newFont = new Font(Font.DIALOG, Font.PLAIN, 14);
		g2.setFont(newFont);
		int squareSize = getFont().getSize() + SQUARE_OFFSET;
		if (bottomPlayer == Chess.WHITE) { // a-h in the bottom line, 1-8 in the rightmost file from bottom to top
			for (int x = 0; x < Chess.NUM_OF_COLS; x++) {
				if (x % 2 == 0) {
					g2.setColor(whiteSquareColor);
				} else {
					g2.setColor(blackSquareColor);
				}
				g2.drawChars(LETTERS[x], 0, 1, x * squareSize + 1, 8 * squareSize - 3);
			}
			for (int y = 0; y < Chess.NUM_OF_ROWS; y++) {
				if (y % 2 == 0) {
					g2.setColor(whiteSquareColor);
				} else {
					g2.setColor(blackSquareColor);
				}
				g2.drawChars(NUMBERS[Chess.NUM_OF_ROWS - 1 - y], 0, 1, 8 * squareSize - 10, y * squareSize + 14);
			}
		} else { //h-a in the bottom line, 1-8 in the rightmost file from top to bottom
			for (int x = 0; x < Chess.NUM_OF_COLS; x++) {
				if (x % 2 == 0) {
					g2.setColor(whiteSquareColor);
				} else {
					g2.setColor(blackSquareColor);
				}
				g2.drawChars(LETTERS[Chess.NUM_OF_COLS - 1 - x], 0, 1, x * squareSize + 2, 8 * squareSize - 2);
			}
			for (int y = 0; y < Chess.NUM_OF_ROWS; y++) {
				if (y % 2 == 0) {
					g2.setColor(whiteSquareColor);
				} else {
					g2.setColor(blackSquareColor);
				}
				g2.drawChars(NUMBERS[y], 0, 1, 8 * squareSize - 12, y * squareSize + 16);
			}
		}
		g2.setFont(oldFont);
	}

	public static String getDefaultFontname() {
		return DEFAULT_FONT_NAME;
	}

	public static int getDefaultFontSize() {
		return DEFAULT_FONT_SIZE;
	}

	@Override
	public boolean doBoardScreenShot(String fileName) {
		return ScreenShot.saveScreenShot(this, fileName);
	}
}
