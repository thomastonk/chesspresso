package chesspresso.position.view;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.RoundRectangle2D;

import chesspresso.Chess;

public class DecorationFactory {

    private DecorationFactory() {
    }

    static public Decoration getArrowDecoration(int from, int to, Color color) {
	return new Arrow(from, to, color);
    }

    static public Decoration getBorderDecoration(int square, Color color) {
	return new Border(square, color);
    }

    static public Decoration getCircleDecoration(int square, Color color) {
	return new Circle(square, color);
    }

    static public Decoration getStrokeDecoration(int from, int to, Color color) {
	return new Stroke(from, to, color);
    }

    static class Arrow implements Decoration {
	private final int from;
	private final int to;
	private final Color color;

	Arrow(int from, int to, Color color) {
	    this.from = from;
	    this.to = to;
	    this.color = color;
	}

	@Override
	public void paint(Graphics2D g, int squareSize) {
	    g.setColor(color);
	    g.setStroke(new BasicStroke(squareSize / 6, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

	    int from_col = Chess.sqiToCol(from);
	    int from_row = 7 - Chess.sqiToRow(from);
	    int to_col = Chess.sqiToCol(to);
	    int to_row = 7 - Chess.sqiToRow(to);

	    int x1 = squareSize * from_col + squareSize / 2;
	    int y1 = squareSize * from_row + squareSize / 2;
	    int x2 = squareSize * to_col + squareSize / 2;
	    int y2 = squareSize * to_row + squareSize / 2;
	    Line2D.Double line = new Line2D.Double(x1, y1, x2, y2);

	    Polygon arrowHead = new Polygon();
	    arrowHead.addPoint(0, 0);
	    arrowHead.addPoint(-squareSize / 4, -squareSize / 2);
	    arrowHead.addPoint(squareSize / 4, -squareSize / 2);

	    double length = Math
		    .sqrt((from_col - to_col) * (from_col - to_col) + (from_row - to_row) * (from_row - to_row));
	    Line2D.Double arrowBody = new Line2D.Double(0, -squareSize / 2, 0, -length * squareSize);

	    AffineTransform tx = new AffineTransform(g.getTransform());
	    double angle = Math.atan2(line.y2 - line.y1, line.x2 - line.x1);
	    tx.translate(line.x2, line.y2);
	    tx.rotate((angle - Math.PI / 2d));
	    Graphics2D g2 = (Graphics2D) g.create();
	    g2.setTransform(tx);
	    g2.fill(arrowHead);
	    g2.draw(arrowBody);
	    g2.dispose();
	}

	@Override
	public Color getColor() {
	    return color;
	}

	@Override
	public DecorationType getType() {
	    return DecorationType.ARROW;
	}
    }

    static class Border implements Decoration {
	private final int square;
	private final Color color;

	Border(int square, Color color) {
	    this.square = square;
	    this.color = color;
	}

	@Override
	public void paint(Graphics2D g, int squareSize) {
	    g.setColor(color);
	    g.setStroke(new BasicStroke(squareSize / 8));
	    int square_col = Chess.sqiToCol(square);
	    int square_row = 7 - Chess.sqiToRow(square);
	    g.draw(new RoundRectangle2D.Double(square_col * squareSize + squareSize / 16,
		    square_row * squareSize + squareSize / 16, squareSize - squareSize / 8, squareSize - squareSize / 8,
		    squareSize / 8, squareSize / 8));
	}

	@Override
	public Color getColor() {
	    return color;
	}

	@Override
	public DecorationType getType() {
	    return DecorationType.BORDER;
	}
    }

    static class Circle implements Decoration {
	private final int square;
	private final Color color;

	Circle(int square, Color color) {
	    this.square = square;
	    this.color = color;
	}

	@Override
	public void paint(Graphics2D g, int squareSize) {
	    g.setColor(color);
	    int square_col = Chess.sqiToCol(square);
	    int square_row = 7 - Chess.sqiToRow(square);
	    Ellipse2D.Double circle = new Ellipse2D.Double(squareSize * square_col + squareSize / 2 - squareSize / 6,
		    squareSize * square_row + squareSize / 2 - squareSize / 6, squareSize / 3, squareSize / 3);
	    g.fill(circle);
	}

	@Override
	public Color getColor() {
	    return color;
	}

	@Override
	public DecorationType getType() {
	    return DecorationType.CIRCLE;
	}
    }

    static class Stroke implements Decoration {
	private final int from;
	private final int to;
	private final Color color;

	Stroke(int from, int to, Color color) {
	    this.from = from;
	    this.to = to;
	    this.color = color;
	}

	@Override
	public void paint(Graphics2D g, int squareSize) {
	    g.setColor(color);
	    g.setStroke(new BasicStroke(squareSize / 6, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

	    int from_col = Chess.sqiToCol(from);
	    int from_row = 7 - Chess.sqiToRow(from);
	    int to_col = Chess.sqiToCol(to);
	    int to_row = 7 - Chess.sqiToRow(to);
	    g.drawLine(squareSize * from_col + squareSize / 2, squareSize * from_row + squareSize / 2,
		    squareSize * to_col + squareSize / 2, squareSize * to_row + squareSize / 2);
	}

	@Override
	public Color getColor() {
	    return color;
	}

	@Override
	public DecorationType getType() {
	    return DecorationType.STROKE;
	}
    }
}
