package com.tilelayers.labels;

import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.image.*;
import java.awt.image.renderable.RenderableImage;
import java.text.AttributedCharacterIterator;
import java.text.AttributedString;
import java.util.Map;
import java.util.function.Consumer;

/** Records text, and optionally Ground Items' label graphics, for the late pass. */
final class DeferredTextGraphics extends Graphics2D
{
    private final Graphics2D delegate;
    private final DeferredOverlayText text;
    private final boolean ownsDelegate;
    private final boolean groundItemLabels;

    DeferredTextGraphics(Graphics2D delegate, DeferredOverlayText text) { this(delegate, text, false); }
    DeferredTextGraphics(Graphics2D delegate, DeferredOverlayText text, boolean groundItemLabels)
    { this(delegate, text, groundItemLabels, false); }
    private DeferredTextGraphics(Graphics2D delegate, DeferredOverlayText text, boolean groundItemLabels, boolean ownsDelegate)
    { this.delegate = delegate; this.text = text; this.groundItemLabels = groundItemLabels; this.ownsDelegate = ownsDelegate; }

    private void labelGraphic(Consumer<Graphics2D> operation)
    {
        if (groundItemLabels) text.add(delegate, operation);
        else operation.accept(delegate);
    }

    private void shape(Shape shape, boolean fill)
    {
        // Scoped to the pinned Ground Items overlay: tile highlights are
        // Polygons; label backgrounds/controls and timer pies are other shapes.
        // Never apply this distinction to arbitrary marker overlays.
        if (groundItemLabels && !(shape instanceof Polygon))
        {
            Shape copy = shape instanceof java.awt.geom.RectangularShape
                ? (Shape) ((java.awt.geom.RectangularShape) shape).clone() : new Path2D.Double(shape);
            text.add(delegate, g -> { if (fill) g.fill(copy); else g.draw(copy); });
        }
        else if (fill) delegate.fill(shape);
        else delegate.draw(shape);
    }

    @Override public void draw(Shape shape) { shape(shape, false); }
    @Override public void fill(Shape shape) { shape(shape, true); }
    @Override public Graphics create() { return new DeferredTextGraphics((Graphics2D) delegate.create(), text, groundItemLabels, true); }
    // Graphics.finalize() also calls dispose(). The root borrows RuneLite's
    // cached context; only children created by this wrapper own their context.
    @Override public void dispose() { if(ownsDelegate)delegate.dispose(); }
    @Override public void setColor(Color c) { delegate.setColor(c); }
    @Override public Color getColor() { return delegate.getColor(); }
    @Override public void setPaintMode() { delegate.setPaintMode(); }
    @Override public void setXORMode(Color c) { delegate.setXORMode(c); }
    @Override public void setFont(Font f) { delegate.setFont(f); }
    @Override public Font getFont() { return delegate.getFont(); }
    @Override public FontMetrics getFontMetrics(Font f) { return delegate.getFontMetrics(f); }
    @Override public Rectangle getClipBounds() { return delegate.getClipBounds(); }
    @Override public void clipRect(int x,int y,int w,int h) { delegate.clipRect(x,y,w,h); }
    @Override public void setClip(int x,int y,int w,int h) { delegate.setClip(x,y,w,h); }
    @Override public Shape getClip() { return delegate.getClip(); }
    @Override public void setClip(Shape s) { delegate.setClip(s); }
    @Override public void copyArea(int x,int y,int w,int h,int dx,int dy) { delegate.copyArea(x,y,w,h,dx,dy); }
    @Override public void drawLine(int x1,int y1,int x2,int y2) { labelGraphic(g -> g.drawLine(x1,y1,x2,y2)); }
    @Override public void drawRect(int x,int y,int w,int h) { labelGraphic(g -> g.drawRect(x,y,w,h)); }
    @Override public void fillRect(int x,int y,int w,int h) { labelGraphic(g -> g.fillRect(x,y,w,h)); }
    @Override public void clearRect(int x,int y,int w,int h) { delegate.clearRect(x,y,w,h); }
    @Override public void drawRoundRect(int x,int y,int w,int h,int aw,int ah) { labelGraphic(g -> g.drawRoundRect(x,y,w,h,aw,ah)); }
    @Override public void fillRoundRect(int x,int y,int w,int h,int aw,int ah) { labelGraphic(g -> g.fillRoundRect(x,y,w,h,aw,ah)); }
    @Override public void drawOval(int x,int y,int w,int h) { labelGraphic(g -> g.drawOval(x,y,w,h)); }
    @Override public void fillOval(int x,int y,int w,int h) { labelGraphic(g -> g.fillOval(x,y,w,h)); }
    @Override public void drawArc(int x,int y,int w,int h,int start,int angle) { labelGraphic(g -> g.drawArc(x,y,w,h,start,angle)); }
    @Override public void fillArc(int x,int y,int w,int h,int start,int angle) { labelGraphic(g -> g.fillArc(x,y,w,h,start,angle)); }
    @Override public void drawPolyline(int[] x,int[] y,int n) { delegate.drawPolyline(x,y,n); }
    @Override public void drawPolygon(int[] x,int[] y,int n) { delegate.drawPolygon(x,y,n); }
    @Override public void fillPolygon(int[] x,int[] y,int n) { delegate.fillPolygon(x,y,n); }
    @Override public void drawPolygon(Polygon p) { delegate.drawPolygon(p); }
    @Override public void fillPolygon(Polygon p) { delegate.fillPolygon(p); }
    @Override public void drawString(String s,int x,int y) { text.add(delegate, g -> g.drawString(s,x,y)); }
    @Override public void drawString(String s,float x,float y) { text.add(delegate, g -> g.drawString(s,x,y)); }
    @Override public void drawString(AttributedCharacterIterator s,int x,int y) { drawString(s,(float)x,(float)y); }
    @Override public void drawString(AttributedCharacterIterator s,float x,float y) { AttributedCharacterIterator copy = new AttributedString(s).getIterator(); text.add(delegate, g -> g.drawString(copy,x,y)); }
    @Override public void drawGlyphVector(GlyphVector original,float x,float y)
    {
        int count = original.getNumGlyphs();
        GlyphVector copy = original.getFont().createGlyphVector(original.getFontRenderContext(), original.getGlyphCodes(0,count,null));
        for (int i=0; i<=count; i++) copy.setGlyphPosition(i,original.getGlyphPosition(i));
        for (int i=0; i<count; i++) copy.setGlyphTransform(i,original.getGlyphTransform(i));
        text.add(delegate, g -> g.drawGlyphVector(copy,x,y));
    }
    @Override public void drawChars(char[] data,int offset,int length,int x,int y) { drawString(new String(data,offset,length),x,y); }
    @Override public void drawBytes(byte[] data,int offset,int length,int x,int y)
    { drawString(new String(data,offset,length,java.nio.charset.StandardCharsets.ISO_8859_1),x,y); }
    @Override public boolean drawImage(Image i,int x,int y,ImageObserver o) { return delegate.drawImage(i,x,y,o); }
    @Override public boolean drawImage(Image i,int x,int y,int w,int h,ImageObserver o) { return delegate.drawImage(i,x,y,w,h,o); }
    @Override public boolean drawImage(Image i,int x,int y,Color c,ImageObserver o) { return delegate.drawImage(i,x,y,c,o); }
    @Override public boolean drawImage(Image i,int x,int y,int w,int h,Color c,ImageObserver o) { return delegate.drawImage(i,x,y,w,h,c,o); }
    @Override public boolean drawImage(Image i,int dx1,int dy1,int dx2,int dy2,int sx1,int sy1,int sx2,int sy2,ImageObserver o)
    { return delegate.drawImage(i,dx1,dy1,dx2,dy2,sx1,sy1,sx2,sy2,o); }
    @Override public boolean drawImage(Image i,int dx1,int dy1,int dx2,int dy2,int sx1,int sy1,int sx2,int sy2,Color c,ImageObserver o)
    { return delegate.drawImage(i,dx1,dy1,dx2,dy2,sx1,sy1,sx2,sy2,c,o); }
    @Override public boolean drawImage(Image i,AffineTransform t,ImageObserver o) { return delegate.drawImage(i,t,o); }
    @Override public void drawImage(BufferedImage i,BufferedImageOp op,int x,int y) { delegate.drawImage(i,op,x,y); }
    @Override public void drawRenderedImage(RenderedImage i,AffineTransform t) { delegate.drawRenderedImage(i,t); }
    @Override public void drawRenderableImage(RenderableImage i,AffineTransform t) { delegate.drawRenderableImage(i,t); }
    @Override public void translate(int x,int y) { delegate.translate(x,y); }
    @Override public void translate(double x,double y) { delegate.translate(x,y); }
    @Override public void rotate(double a) { delegate.rotate(a); }
    @Override public void rotate(double a,double x,double y) { delegate.rotate(a,x,y); }
    @Override public void scale(double x,double y) { delegate.scale(x,y); }
    @Override public void shear(double x,double y) { delegate.shear(x,y); }
    @Override public void transform(AffineTransform t) { delegate.transform(t); }
    @Override public void setTransform(AffineTransform t) { delegate.setTransform(t); }
    @Override public AffineTransform getTransform() { return delegate.getTransform(); }
    @Override public void setBackground(Color c) { delegate.setBackground(c); }
    @Override public Color getBackground() { return delegate.getBackground(); }
    @Override public void setStroke(Stroke s) { delegate.setStroke(s); }
    @Override public Stroke getStroke() { return delegate.getStroke(); }
    @Override public void setComposite(Composite c) { delegate.setComposite(c); }
    @Override public Composite getComposite() { return delegate.getComposite(); }
    @Override public void setPaint(Paint p) { delegate.setPaint(p); }
    @Override public Paint getPaint() { return delegate.getPaint(); }
    @Override public void clip(Shape s) { delegate.clip(s); }
    @Override public FontRenderContext getFontRenderContext() { return delegate.getFontRenderContext(); }
    @Override public GraphicsConfiguration getDeviceConfiguration() { return delegate.getDeviceConfiguration(); }
    @Override public boolean hit(Rectangle r,Shape s,boolean stroke) { return delegate.hit(r,s,stroke); }
    @Override public void setRenderingHint(RenderingHints.Key k,Object v) { delegate.setRenderingHint(k,v); }
    @Override public Object getRenderingHint(RenderingHints.Key k) { return delegate.getRenderingHint(k); }
    @Override public void setRenderingHints(Map<?,?> hints) { delegate.setRenderingHints(hints); }
    @Override public void addRenderingHints(Map<?,?> hints) { delegate.addRenderingHints(hints); }
    @Override public RenderingHints getRenderingHints() { return delegate.getRenderingHints(); }
}
