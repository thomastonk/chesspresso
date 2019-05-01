/*******************************************************************************
 * Basic version: Copyright (C) 2003 Bernhard Seybold. All rights reserved.
 * All changes since then: Copyright (C) 2019 Thomas Niessen. All rights reserved.
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
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.GeneralPath;
import java.awt.geom.PathIterator;
import java.util.ArrayList;

import chesspresso.Chess;
import chesspresso.position.AbstractMutablePosition;
import chesspresso.position.PositionListener;
import chesspresso.position.PositionMotionListener;

/**
 * Position view.
 * 
 * @author Bernhard Seybold
 * @version $Revision: 1.2 $
 */
@SuppressWarnings("serial")
public class PositionView extends java.awt.Component implements PositionListener, MouseListener, MouseMotionListener {
    private int m_bottom;
    private AbstractMutablePosition m_position;
    @SuppressWarnings("unused")
    private boolean m_showSqiEP;
    private Color m_whiteSquareColor;
    private Color m_blackSquareColor;
    private Color m_whiteColor;
    private Color m_blackColor;
    private boolean m_solidStones;

    private int m_draggedFrom;
    private int m_draggedStone;
    private int m_draggedX, m_draggedY;
    private int m_draggedPartnerSqi;
    private PositionMotionListener m_positionMotionListener;

    final static private Color m_whiteSquareDefaultColor = new Color(232, 219, 200);
    final static private Color m_blackSquareDefaultColor = new Color(224, 175, 100);

    final static private int squareOffset = 4;

    // ======================================================================

    /**
     * Create a new position view.
     * 
     * @param position the position to display
     */
    public PositionView(AbstractMutablePosition position) {
	this(position, Chess.WHITE);
    }

    /**
     * Create a new position view.
     * 
     * @param position     the position to display
     * @param bottomPlayer the player at the lower edge
     */
    public PositionView(AbstractMutablePosition position, int bottomPlayer) {
	m_position = position;
	m_bottom = bottomPlayer;
	m_showSqiEP = false;
	m_whiteSquareColor = m_whiteSquareDefaultColor;
	m_blackSquareColor = m_blackSquareDefaultColor;
	m_whiteColor = Color.BLACK;
	m_blackColor = Color.BLACK;
	m_solidStones = true;

	setFont(new Font("Arial Unicode MS", Font.PLAIN, 32));
	// If this font is not available, "Dialog" will be chosen.

	m_draggedStone = Chess.NO_STONE;
	m_draggedFrom = Chess.NO_SQUARE;
	m_draggedPartnerSqi = Chess.NO_SQUARE;
	m_positionMotionListener = null;
	m_position.addPositionListener(this); // TODO: when do we remove it?
	addMouseListener(this);
	addMouseMotionListener(this);
    }

    // ======================================================================
    public void setPosition(AbstractMutablePosition position) {
	m_position.removePositionListener(this);
	m_position = position;
	m_position.addPositionListener(this);
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
     * Flip the sides.
     */
    public void flip() {
	setBottomPlayer(Chess.otherPlayer(m_bottom));
    }

    /**
     * Determines whether or not the en passant square should be marked. NOT YET
     * IMPLEMENTED.
     * 
     * @param showSqiEP whether or not to mark the en passant square
     */
    public void setShowSqiEP(boolean showSqiEP) {
	m_showSqiEP = showSqiEP;
	sqiEPChanged(m_position.getSqiEP());
    }

    public AbstractMutablePosition getPosition() {
	return m_position;
    }

    @Override
    public Dimension getPreferredSize() {
	return new Dimension(8 * (getFont().getSize() + squareOffset), 8 * (getFont().getSize() + squareOffset));
    }

    @Override
    public Dimension getMinimumSize() {
	return new Dimension(8 * (getFont().getSize() + squareOffset), 8 * (getFont().getSize() + squareOffset));
    }

    @Override
    public Dimension getMaximumSize() {
	return new Dimension(8 * (getFont().getSize() + squareOffset), 8 * (getFont().getSize() + squareOffset));
    }

    public int getSquareSize() {
	return getFont().getSize() + squareOffset;
    }

    // ======================================================================
    // interface PositionListener

    @Override
    public void squareChanged(int sqi, int stone) {
	repaint();
    }

    @Override
    public void toPlayChanged(int toPlay) {
    }

    @Override
    public void castlesChanged(int castles) {
    }

    @Override
    public void sqiEPChanged(int sqiEP) {
    }

    @Override
    public void plyNumberChanged(int plyNumber) {
    }

    @Override
    public void halfMoveClockChanged(int halfMoveClock) {
    }

    // ======================================================================

    public void setPositionMotionListener(PositionMotionListener listener) {
	m_positionMotionListener = listener;
    }

    private int getSquareForEvent(MouseEvent evt) {
	int size = getFont().getSize() + squareOffset;
	return (m_bottom == Chess.WHITE ? Chess.coorToSqi(evt.getX() / size, Chess.NUM_OF_ROWS - evt.getY() / size - 1)
		: Chess.coorToSqi(Chess.NUM_OF_COLS - 1 - evt.getX() / size, evt.getY() / size));
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
	if (m_positionMotionListener == null)
	    return;
	m_draggedFrom = getSquareForEvent(e);
	if (m_positionMotionListener.allowDrag(m_position, m_draggedFrom)) {
	    m_draggedStone = m_position.getStone(m_draggedFrom);
	    m_draggedX = e.getX();
	    m_draggedY = e.getY();
	    m_draggedPartnerSqi = m_positionMotionListener.getPartnerSqi(m_position, m_draggedFrom);
	    // TODO mark m_draggedPartnerSqi
	    repaint();
	} else {
	    m_positionMotionListener.squareClicked(m_position, m_draggedFrom, e);
	    m_draggedFrom = Chess.NO_SQUARE;
	}
    }

    @Override
    public void mouseReleased(MouseEvent e) {
	if (m_positionMotionListener == null)
	    return;
	if (m_draggedFrom != Chess.NO_SQUARE) {
	    int draggedTo = getSquareForEvent(e);
	    if (draggedTo != Chess.NO_SQUARE) {
		if (m_draggedFrom == draggedTo) {
		    if (m_draggedPartnerSqi != Chess.NO_SQUARE) {
			m_positionMotionListener.dragged(m_position, m_draggedFrom, m_draggedPartnerSqi, e);
		    } else {
			m_positionMotionListener.squareClicked(m_position, m_draggedFrom, e);
		    }
		} else {
		    m_positionMotionListener.dragged(m_position, m_draggedFrom, draggedTo, e);
		}
	    }
	    m_draggedFrom = Chess.NO_SQUARE;
	    m_draggedStone = Chess.NO_STONE;
	    // TODO unmark m_draggedPartnerSqi
	    repaint();
	}
    }

    @Override
    public void mouseDragged(MouseEvent e) {
	if (m_draggedFrom != Chess.NO_SQUARE) {
	    m_draggedX = e.getX();
	    m_draggedY = e.getY();
	    repaint();
	}
    }

    @Override
    public void mouseMoved(MouseEvent e) {
    }

    private final static String[] whiteStoneStrings = { "\u2654", "\u2659", "\u2655", "\u2656", "\u2657", "\u2658", " ",
	    "\u2658", "\u2657", "\u2656", "\u2655", "\u2659", "\u2654" },
	    blackStoneStrings = { "\u265A", "\u265F", "\u265B", "\u265C", "\u265D", "\u265E", " ", "\u265E", "\u265D",
		    "\u265C", "\u265B", "\u265F", "\u265A" };

    private static String getStringForStone(int stone, boolean isWhite) {
	String s = isWhite ? whiteStoneStrings[stone - Chess.MIN_STONE] : blackStoneStrings[stone - Chess.MIN_STONE];
	return s;
    }

    public static String getStringForStone(int stone) {
	return stone < 0 ? whiteStoneStrings[stone - Chess.MIN_STONE] : blackStoneStrings[stone - Chess.MIN_STONE];
    }

    @Override
    public void paint(Graphics graphics) {
	super.paint(graphics);
	int squareSize = getFont().getSize() + squareOffset;
	for (int y = 0; y < Chess.NUM_OF_ROWS; y++) {
	    for (int x = 0; x < Chess.NUM_OF_COLS; x++) {
		// First step: draw the background
		int sqi = (m_bottom == Chess.WHITE ? Chess.coorToSqi(x, Chess.NUM_OF_ROWS - y - 1)
			: Chess.coorToSqi(Chess.NUM_OF_COLS - x - 1, y));
		if (Chess.isWhiteSquare(sqi)) {
		    graphics.setColor(m_whiteSquareColor);
		    graphics.fillRect(x * squareSize, y * squareSize, squareSize, squareSize);
		} else {
		    graphics.setColor(m_blackSquareColor);
		    graphics.fillRect(x * squareSize, y * squareSize, squareSize, squareSize);
		}
		// Second step: get the stone and paint over the background with white color.
		int stone = (sqi == m_draggedFrom ? Chess.NO_STONE : m_position.getStone(sqi));
		boolean stoneIsWhite = Chess.stoneToColor(stone) == Chess.WHITE;
		graphics.setColor(stoneIsWhite ? m_whiteColor : m_blackColor);
		if (m_solidStones) {
		    stone = Chess.pieceToStone(Chess.stoneToPiece(stone), Chess.BLACK);
		}
		Graphics2D g2 = (Graphics2D) graphics;
		g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
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
		// Third step: draw the stone's image
		g2.setColor(color);
		graphics.drawString(getStringForStone(stone, stoneIsWhite), transX, transY + 1); // <- mystical
		// I introduced text anti-aliasing, which improves the layout. But the mystical
		// +1 is not understood.
	    }
	}

	if (m_draggedStone != Chess.NO_STONE) {
	    // That's the old implementation. The first line does nothing, because the two
	    // colors
	    // are equal.
//			graphics.setColor(Chess.stoneToColor(m_draggedStone) == Chess.WHITE ? m_whiteColor : m_blackColor);
//			graphics.drawString(getStringForStone(m_draggedStone, true), m_draggedX - size / 2, m_draggedY + size / 2);
	    // End of old implementation.
	    // NEW:
	    // Again first the background
	    Graphics2D g2 = (Graphics2D) graphics;
	    g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
	    FontRenderContext frc = g2.getFontRenderContext();
	    GlyphVector gv = getFont().createGlyphVector(frc,
		    getStringForStone(m_draggedStone, Chess.stoneToColor(m_draggedStone) == Chess.WHITE));

	    final Shape shape = gv.getOutline();
	    int transX = m_draggedX - squareSize / 2;
	    int transY = m_draggedY + squareSize / 2;
	    final AffineTransform trans = AffineTransform.getTranslateInstance(transX, transY);
	    final Shape shapeCentered = trans.createTransformedShape(shape);
	    final Area shapeArea = new Area(shapeCentered);
	    Color color = g2.getColor();
	    g2.setColor(Color.WHITE);
	    final ArrayList<Shape> partsOfShape = getAllPartsOfTheShape(shapeArea);
	    for (final Shape part : partsOfShape) {
		g2.fill(part);
	    }
	    // and then the piece itself
	    g2.setColor(color);
	    graphics.drawString(getStringForStone(m_draggedStone, Chess.stoneToColor(m_draggedStone) == Chess.WHITE),
		    m_draggedX - squareSize / 2, m_draggedY + squareSize / 2);
	}
    }

    public static ArrayList<Shape> getAllPartsOfTheShape(final Shape shape) {
	final ArrayList<Shape> partsOfShape = new ArrayList<>();

	GeneralPath generalPath = new GeneralPath();
	for (final PathIterator pathIterater = shape.getPathIterator(null); !pathIterater.isDone(); pathIterater
		.next()) {
	    final double[] coords = new double[6];
	    final int pathSegmentType = pathIterater.currentSegment(coords);
	    final int windingRule = pathIterater.getWindingRule();
	    generalPath.setWindingRule(windingRule);
	    switch (pathSegmentType) {
	    case PathIterator.SEG_MOVETO:
		generalPath = new GeneralPath();
		generalPath.setWindingRule(windingRule);
		generalPath.moveTo(coords[0], coords[1]);
		break;
	    case PathIterator.SEG_LINETO:
		generalPath.lineTo(coords[0], coords[1]);
		break;
	    case PathIterator.SEG_QUADTO:
		generalPath.quadTo(coords[0], coords[1], coords[2], coords[3]);
		break;
	    case PathIterator.SEG_CUBICTO:
		generalPath.curveTo(coords[0], coords[1], coords[2], coords[3], coords[4], coords[5]);
		break;
	    case PathIterator.SEG_CLOSE:
		generalPath.closePath();
		partsOfShape.add(new Area(generalPath));
		break;
	    default:
		break;
	    }
	}

	return partsOfShape;
    }

}
