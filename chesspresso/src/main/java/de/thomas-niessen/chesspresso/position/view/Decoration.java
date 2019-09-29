package chesspresso.position.view;

import java.awt.Color;
import java.awt.Graphics2D;

public interface Decoration {

    public enum DecorationType {
	ARROW, BORDER, CIRCLE, STROKE
    };

    void paint(Graphics2D g, int squareSize);

    Color getColor();

    DecorationType getType();

}
