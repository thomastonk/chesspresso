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
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import chesspresso.Chess;
import chesspresso.Mouse;
import chesspresso.ScreenShot;
import chesspresso.game.view.UserAction;
import chesspresso.position.ImmutablePosition;
import chesspresso.position.Position;
import chesspresso.position.PositionListener;
import chesspresso.position.PositionMotionListener;
import chesspresso.position.view.Decoration.DecorationType;

/**
 * Position view.
 * 
 * @author Bernhard Seybold
 */
@SuppressWarnings("serial")
public class PositionView extends JPanel implements PositionListener, MouseListener, MouseMotionListener {
	private Position m_position;
	private int m_bottom;
	private UserAction m_userAction;
	private Color m_whiteSquareColor;
	private Color m_blackSquareColor;
	private Color m_whiteColor;
	private Color m_blackColor;
	private boolean m_solidStones;

	private int m_draggedFrom;
	private int m_draggedStone;
	private int m_draggedX, m_draggedY;
	private PositionMotionListener m_positionMotionListener;

	final static private Color m_whiteSquareDefaultColor = new Color(232, 219, 200);
	final static private Color m_blackSquareDefaultColor = new Color(224, 175, 100);

	final static private BufferedImage m_highlightWhiteDefault;
	final static private BufferedImage m_highlightBlackDefault;

	static {
		{
			m_highlightWhiteDefault = new BufferedImage(5, 5, BufferedImage.TYPE_INT_RGB);
			Graphics2D g2d = m_highlightWhiteDefault.createGraphics();
			g2d.setColor(m_blackSquareDefaultColor);
			g2d.fillRect(0, 0, 5, 5);
			g2d.setColor(m_whiteSquareDefaultColor);
			g2d.fillOval(0, 0, 5, 5);
		}
		{
			m_highlightBlackDefault = new BufferedImage(5, 5, BufferedImage.TYPE_INT_RGB);
			Graphics2D g2d = m_highlightBlackDefault.createGraphics();
			g2d.setColor(m_whiteSquareDefaultColor);
			g2d.fillRect(0, 0, 5, 5);
			g2d.setColor(m_blackSquareDefaultColor);
			g2d.fillOval(0, 0, 5, 5);
		}
	}

	final static private Paint m_highlightPaintWhite = new TexturePaint(m_highlightWhiteDefault, new Rectangle(0, 0, 5, 5));
	final static private Paint m_highlightPaintBlack = new TexturePaint(m_highlightBlackDefault, new Rectangle(0, 0, 5, 5));

	final static private int squareOffset = 4;

	final private Map<Integer, Paint> m_backgroundPaints = new HashMap<>();

	final private Object decorationToken = new Object();
	final private List<Decoration> lowerLevel = new ArrayList<>(); // below the figure symbols
	final private List<Decoration> upperLevel = new ArrayList<>(); // above the figure symbols

	final static private Color GREEN_TRANSPARENT = new Color(0.f, 1.f, 0.f, 0.6f);
	final static private Color YELLOW_TRANSPARENT = new Color(1.f, 1.f, 0.f, 0.6f);
	final static private Color RED_TRANSPARENT = new Color(1.f, 0.f, 0.f, 0.6f);

	final static private String DEFAULT_FONT_NAME = "CS Chess Merida Unicode";
	final static private int DEFAULT_FONT_SIZE = 32;

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
		m_position = position;
		m_bottom = bottomPlayer;
		m_userAction = userAction;
		m_whiteSquareColor = m_whiteSquareDefaultColor;
		m_blackSquareColor = m_blackSquareDefaultColor;
		m_whiteColor = Color.BLACK;
		m_blackColor = Color.BLACK;
		m_solidStones = true;

		setFont(new Font(DEFAULT_FONT_NAME, Font.PLAIN, fontSize));
		// If this font is not available, "Dialog" will be chosen.

		m_draggedStone = Chess.NO_STONE;
		m_draggedFrom = Chess.NO_SQUARE;
		m_positionMotionListener = null;
		m_position.addPositionListener(this); // Seybold: when do we remove it?
		addMouseListener(this);
		addMouseMotionListener(this);
	}

	// ======================================================================
	public void setPosition(Position position) {
		m_position.removePositionListener(this);
		m_position = position;
		m_position.addPositionListener(this);
	}

	// ======================================================================
	public void setImmutablePosition(Position position) {
		m_position = position;
	}

	// ======================================================================

	public int getBottomPlayer() {
		return m_bottom;
	}

	public void setBottomPlayer(int player) {
		if (player != m_bottom) {
			m_bottom = player;
			repaint();
		}
	}

	public void setUserAction(UserAction userAction) {
		m_userAction = userAction;
	}

	public Color getWhiteSquareColor() {
		return m_whiteSquareColor;
	}

	public Color getBlackSquareColor() {
		return m_blackSquareColor;
	}

	public Color getWhiteColor() {
		return m_whiteColor;
	}

	public Color getBlackColor() {
		return m_blackColor;
	}

	public boolean getSolidStones() {
		return m_solidStones;
	}

	public void setWhiteSquareColor(Color color) {
		if (m_whiteSquareColor == color)
			return;
		m_whiteSquareColor = color;
		repaint();
	}

	public void setBlackSquareColor(Color color) {
		if (m_blackSquareColor == color)
			return;
		m_blackSquareColor = color;
		repaint();
	}

	public void setWhiteSquareColorToDefault() {
		m_whiteSquareColor = m_whiteSquareDefaultColor;
		repaint();
	}

	public void setBlackSquareColorToDefault() {
		m_blackSquareColor = m_blackSquareDefaultColor;
		repaint();
	}

	public void setWhiteColor(Color color) {
		if (m_whiteColor == color)
			return;
		m_whiteColor = color;
		repaint();
	}

	public void setBlackColor(Color color) {
		if (m_blackColor == color)
			return;
		m_blackColor = color;
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
		if (solid == m_solidStones)
			return;
		m_solidStones = solid;
		repaint();
	}

	/**
	 * Flip board.
	 */
	public void flip() {
		setBottomPlayer(Chess.otherPlayer(m_bottom));
	}

	public Position getPosition() {
		return m_position;
	}

	@Override
	public Dimension getPreferredSize() {
		return new Dimension(Chess.NUM_OF_COLS * (getFont().getSize() + squareOffset),
				Chess.NUM_OF_ROWS * (getFont().getSize() + squareOffset));
	}

	@Override
	public Dimension getMinimumSize() {
		return new Dimension(Chess.NUM_OF_COLS * (getFont().getSize() + squareOffset),
				Chess.NUM_OF_ROWS * (getFont().getSize() + squareOffset));
	}

	@Override
	public Dimension getMaximumSize() {
		return new Dimension(Chess.NUM_OF_COLS * (getFont().getSize() + squareOffset),
				Chess.NUM_OF_ROWS * (getFont().getSize() + squareOffset));
	}

	public int getSquareSize() {
		return getFont().getSize() + squareOffset;
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
		return Chess.NUM_OF_COLS * (Chess.NUM_OF_ROWS - 1 - y0) + x0;
	}

	public void setOrRemovePaint(int sqi, Paint paint) {
		if (paint == null) {
			m_backgroundPaints.remove(sqi);
		} else {
			m_backgroundPaints.put(sqi, paint);
		}
		repaint();
	}

	public void setHighlight(int sqi, boolean highlight) {
		if (highlight) {
			if (Chess.isWhiteSquare(sqi)) {
				m_backgroundPaints.put(sqi, m_highlightPaintWhite);
			} else {
				m_backgroundPaints.put(sqi, m_highlightPaintBlack);
			}
		} else {
			m_backgroundPaints.remove(sqi);
		}
		repaint();
	}

	public void toggleHighlight(int sqi) {
		if (m_backgroundPaints.containsKey(sqi)) {
			m_backgroundPaints.remove(sqi);
		} else {
			if (Chess.isWhiteSquare(sqi)) {
				m_backgroundPaints.put(sqi, m_highlightPaintWhite);
			} else {
				m_backgroundPaints.put(sqi, m_highlightPaintBlack);
			}
		}
		repaint();
	}

	public void removeAllHighlighting() {
		for (int sqi = Chess.A1; sqi <= Chess.H8; ++sqi) {
			Paint paint = m_backgroundPaints.get(sqi);
			if (paint != null) {
				if (paint == m_highlightPaintWhite || paint == m_highlightPaintBlack) {
					m_backgroundPaints.remove(sqi);
				}
			}
		}
		repaint();
	}

	public void addDecoration(Decoration decoration, boolean onTop) {
		synchronized (decorationToken) {
			if (onTop) {
				upperLevel.add(decoration);
			} else {
				lowerLevel.add(decoration);
			}
		}
		repaint();
	}

	public void removeAllDecorations() {
		synchronized (decorationToken) {
			lowerLevel.clear();
			upperLevel.clear();
		}
		repaint();
	}

	public void removeDecorations(Decoration.DecorationType type, Color color) {
		synchronized (decorationToken) {
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
		}
		repaint();
	}

	// ======================================================================
	// PositionListener

	@Override
	public void positionChanged(ImmutablePosition position) {
		repaint();
	}

	// ======================================================================

	public void setPositionMotionListener(PositionMotionListener listener) {
		m_positionMotionListener = listener;
	}

	public int getSquareForEvent(MouseEvent evt) {
		int size = getFont().getSize() + squareOffset;
		return (m_bottom == Chess.WHITE ? Chess.coorToSqiWithCheck(evt.getX() / size, Chess.NUM_OF_ROWS - evt.getY() / size - 1)
				: Chess.coorToSqiWithCheck(Chess.NUM_OF_COLS - 1 - evt.getX() / size, evt.getY() / size));
		//        : Chess.coorToSqi(Chess.NUM_OF_COLS - evt.getX() / size, evt.getY() / size - 1)); // TN: old version
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
		if (m_userAction == UserAction.DISABLED) {
			return;
		}
		if (Mouse.isSpecial(e) || SwingUtilities.isRightMouseButton(e)) {
			if (e.isAltDown()) {
				m_draggedFrom = getSquareForEvent(e);
			}
			return;
		}
		if (m_positionMotionListener == null)
			return;
		m_draggedFrom = getSquareForEvent(e);
		if (m_positionMotionListener.isDragAllowed(m_position, m_draggedFrom)) {
			m_draggedStone = m_position.getStone(m_draggedFrom);
			m_draggedX = e.getX();
			m_draggedY = e.getY();
			repaint();
		} else {
			m_positionMotionListener.squareClicked(m_position, m_draggedFrom, e);
			m_draggedFrom = Chess.NO_SQUARE;
		}
	}

	private void drawChessbaseDecorations(MouseEvent e) {
		if (e.isAltDown() && m_draggedFrom != Chess.NO_SQUARE) {
			int draggedTo = getSquareForEvent(e);
			if (draggedTo == Chess.NO_SQUARE) {
				return;
			} else if (draggedTo == m_draggedFrom) {
				if (e.isControlDown() || e.isMetaDown()) {
					setOrRemovePaint(draggedTo, YELLOW_TRANSPARENT);
				} else if (e.isShiftDown()) {
					setOrRemovePaint(draggedTo, RED_TRANSPARENT);
				} else {
					setOrRemovePaint(draggedTo, GREEN_TRANSPARENT);
				}
			} else { // arrows
				if (e.isControlDown() || e.isMetaDown()) {
					addDecoration(DecorationFactory.getArrowDecoration(m_draggedFrom, draggedTo, YELLOW_TRANSPARENT), true);
				} else if (e.isShiftDown()) {
					addDecoration(DecorationFactory.getArrowDecoration(m_draggedFrom, draggedTo, RED_TRANSPARENT), true);
				} else {
					addDecoration(DecorationFactory.getArrowDecoration(m_draggedFrom, draggedTo, GREEN_TRANSPARENT), true);
				}
			}
		}
		m_draggedFrom = Chess.NO_SQUARE; // otherwise paint draws there an empty square
		repaint();
	}

	public void removeChessbaseDecorations() {
		Iterator<Map.Entry<Integer, Paint>> iterator = m_backgroundPaints.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<Integer, Paint> entry = iterator.next();
			Paint p = entry.getValue();
			if (p.equals(YELLOW_TRANSPARENT) || p.equals(RED_TRANSPARENT) || p.equals(GREEN_TRANSPARENT)) {
				iterator.remove();
			}
		}
		removeDecorations(DecorationType.ARROW, YELLOW_TRANSPARENT);
		removeDecorations(DecorationType.ARROW, RED_TRANSPARENT);
		removeDecorations(DecorationType.ARROW, GREEN_TRANSPARENT);
	}

	@Override
	public void mouseReleased(MouseEvent e) {
		if (m_userAction == UserAction.DISABLED) {
			return;
		}
		if (Mouse.isSpecial(e)) {
			if (e.isAltDown()) {
				drawChessbaseDecorations(e);
			}
			if (m_draggedStone != Chess.NO_STONE) {
				m_draggedFrom = Chess.NO_SQUARE;
				m_draggedStone = Chess.NO_STONE;
				repaint();
			}
			return;
		}
		if (m_positionMotionListener == null || m_userAction == UserAction.NAVIGABLE)
			return;
		if (m_draggedFrom != Chess.NO_SQUARE) {
			int draggedTo = getSquareForEvent(e);
			if (draggedTo != Chess.NO_SQUARE) {
				if (m_draggedFrom == draggedTo) {
					m_positionMotionListener.squareClicked(m_position, m_draggedFrom, e);
				} else {
					m_positionMotionListener.dragged(m_position, m_draggedFrom, draggedTo, e);
				}
			}
			m_draggedFrom = Chess.NO_SQUARE;
			m_draggedStone = Chess.NO_STONE;
			repaint();
		}
	}

	@Override
	public void mouseDragged(MouseEvent e) {
		if (m_userAction == UserAction.DISABLED) {
			return;
		}
		if (Mouse.isSpecial(e)) {
			if (m_draggedStone != Chess.NO_STONE) {
				m_draggedFrom = Chess.NO_SQUARE;
				m_draggedStone = Chess.NO_STONE;
				repaint();
			}
			return;
		}
		if (m_draggedFrom != Chess.NO_SQUARE) {
			m_draggedX = e.getX();
			m_draggedY = e.getY();
			repaint();
		}
	}

	@Override
	public void mouseMoved(MouseEvent e) {
	}

	private final static String[] whiteStoneStrings = { "\u2654", "\u2659", "\u2655", "\u2656", "\u2657", "\u2658", " ", "\u2658",
			"\u2657", "\u2656", "\u2655", "\u2659", "\u2654" },
			blackStoneStrings = { "\u265A", "\u265F", "\u265B", "\u265C", "\u265D", "\u265E", " ", "\u265E", "\u265D", "\u265C",
					"\u265B", "\u265F", "\u265A" };

	private static String getStringForStone(int stone, boolean isWhite) {
		return isWhite ? whiteStoneStrings[stone - Chess.MIN_STONE] : blackStoneStrings[stone - Chess.MIN_STONE];
	}

	public static String getStringForStone(int stone) {
		return stone < 0 ? whiteStoneStrings[stone - Chess.MIN_STONE] : blackStoneStrings[stone - Chess.MIN_STONE];
	}

	@Override
	public void paintComponent(Graphics graphics) {
		super.paintComponent(graphics);
		Graphics2D g2 = (Graphics2D) graphics;
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		int squareSize = getFont().getSize() + squareOffset;

		// First step: draw the background
		for (int y = 0; y < Chess.NUM_OF_ROWS; y++) {
			for (int x = 0; x < Chess.NUM_OF_COLS; x++) {
				int sqi = (m_bottom == Chess.WHITE ? Chess.coorToSqi(x, Chess.NUM_OF_ROWS - y - 1)
						: Chess.coorToSqi(Chess.NUM_OF_COLS - x - 1, y));
				if (m_backgroundPaints.containsKey(sqi)) {
					g2.setPaint(m_backgroundPaints.get(sqi));
				} else if (Chess.isWhiteSquare(sqi)) {
					graphics.setColor(m_whiteSquareColor);
				} else {
					graphics.setColor(m_blackSquareColor);
				}
				graphics.fillRect(x * squareSize, y * squareSize, squareSize, squareSize);
			}
		}
		// Second step: draw the layer below the stones
		synchronized (decorationToken) {
			for (Decoration decoration : lowerLevel) {
				decoration.paint(g2, squareSize, m_bottom);
			}
		}

		// Third step: get the stone and paint over the background with white color.
		for (int y = 0; y < Chess.NUM_OF_ROWS; y++) {
			for (int x = 0; x < Chess.NUM_OF_COLS; x++) {
				int sqi = (m_bottom == Chess.WHITE ? Chess.coorToSqi(x, Chess.NUM_OF_ROWS - y - 1)
						: Chess.coorToSqi(Chess.NUM_OF_COLS - x - 1, y));
				int stone = (sqi == m_draggedFrom ? Chess.NO_STONE : m_position.getStone(sqi));
				boolean stoneIsWhite = Chess.stoneToColor(stone) == Chess.WHITE;
				graphics.setColor(stoneIsWhite ? m_whiteColor : m_blackColor);
				if (m_solidStones) {
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
		// Fourth step: draw the layer below the stones
		synchronized (decorationToken) {
			for (Decoration decoration : upperLevel) {
				decoration.paint(g2, squareSize, m_bottom);
			}
		}

		// Drag & Drop
		if (m_draggedStone != Chess.NO_STONE) {
			// That's the old implementation. The first line does nothing, because the two
			// colors are equal.
			//			graphics.setColor(Chess.stoneToColor(m_draggedStone) == Chess.WHITE ? m_whiteColor : m_blackColor);
			//			graphics.drawString(getStringForStone(m_draggedStone, true), m_draggedX - size / 2, m_draggedY + size / 2);
			// End of old implementation.
			// NEW: Again first the background
			FontRenderContext frc = g2.getFontRenderContext();
			GlyphVector gv = getFont().createGlyphVector(frc,
					getStringForStone(m_draggedStone, Chess.stoneToColor(m_draggedStone) == Chess.WHITE));

			final Shape shape = gv.getOutline();
			int transX = m_draggedX - squareSize / 2;
			int transY = m_draggedY + squareSize / 2;
			final AffineTransform trans = AffineTransform.getTranslateInstance(transX, transY);
			final Shape shapeCentered = trans.createTransformedShape(shape);
			final Area shapeArea = new Area(shapeCentered);
			g2.setColor(Color.WHITE);
			final ArrayList<Shape> partsOfShape = getAllPartsOfTheShape(shapeArea);
			for (final Shape part : partsOfShape) {
				g2.fill(part);
			}
			// and then the piece itself
			g2.setColor(Chess.stoneToColor(m_draggedStone) == Chess.WHITE ? m_whiteColor : m_blackColor);
			graphics.drawString(getStringForStone(m_draggedStone, Chess.stoneToColor(m_draggedStone) == Chess.WHITE),
					m_draggedX - squareSize / 2, m_draggedY + squareSize / 2);
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

	public static String getDefaultFontname() {
		return DEFAULT_FONT_NAME;
	}

	public static int getDefaultFontSize() {
		return DEFAULT_FONT_SIZE;
	}

	public boolean saveScreenShot(String fileName) {
		return ScreenShot.saveScreenShot(this, fileName);
	}
}
