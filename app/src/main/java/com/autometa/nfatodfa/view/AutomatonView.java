package com.autometa.nfatodfa.view;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Canvas view that draws an automaton (states, arrows, self-loops, labels) and
 * animates it: nodes pop in, edges grow, the active state pulses, freshly added
 * edges glow. Supports pinch-zoom, pan and double-tap reset.
 */
public class AutomatonView extends View {

    // ------------------------------------------------------------------ model

    public static class GNode {
        public final String id;
        public final String label;
        public final String sublabel;          // e.g. "{q0, q1}" — may be null
        public final boolean accept;
        public final boolean trap;
        public final boolean start;

        public float x, y;
        float radius;                          // set by setGraph()
        float appear = 0f;                     // 0..1 scale-in progress
        boolean visible = false;
        ValueAnimator pulseAnim;               // running while highlighted
        float pulsePhase = 0f;

        public GNode(String id, String label, String sublabel,
                     boolean start, boolean accept, boolean trap) {
            this.id = id;
            this.label = label;
            this.sublabel = sublabel;
            this.start = start;
            this.accept = accept;
            this.trap = trap;
        }

        public float radius() { return radius; }
    }

    public static class GEdge {
        public final String from;
        public final String to;
        public String label;                   // may grow during the animation: "a" → "a,b"
        float progress = 0f;                   // 0..1 drawing progress
        boolean visible = false;
        float glow = 0f;                       // 1 = just added (cyan), decays to 0
        ValueAnimator growAnim, glowAnim;

        public GEdge(String from, String to, String label) {
            this.from = from;
            this.to = to;
            this.label = label;
        }
    }

    private final List<GNode> nodes = new ArrayList<>();
    private final List<GEdge> edges = new ArrayList<>();
    private final Map<String, GEdge> edgeByKey = new HashMap<>();   // "from→to"
    private final Map<String, GNode> nodeById = new HashMap<>();

    private final List<ValueAnimator> runningAnims = new ArrayList<>();

    // ------------------------------------------------------------------ paints

    private final Paint edgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edgeGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arrowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint chipFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint chipStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint chipText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint nodeFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint nodeStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint acceptRing = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pulsePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint nodeLabel = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint nodeSub = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private static final int COLOR_EDGE       = 0xFF93A0C4;
    private static final int COLOR_EDGE_GLOW  = 0xFF00E5FF;
    private static final int COLOR_NODE_FILL  = 0xFF18223C;
    private static final int COLOR_NODE_EDGE  = 0xFF6C63FF;
    private static final int COLOR_ACCEPT     = 0xFF00E676;
    private static final int COLOR_TRAP       = 0xFF6B7694;
    private static final int COLOR_TEXT       = 0xFFF2F5FF;
    private static final int COLOR_SUB        = 0xFF9FB0D0;
    private static final int COLOR_PULSE      = 0xFFFFC107;
    private static final int COLOR_CHIP_BG    = 0xCC1B2440;
    private static final int COLOR_GRID       = 0x2A243258;

    private float nodeRadius = 34f;

    // zoom / pan
    private float scaleFactor = 1f, tx = 0f, ty = 0f;
    private final ScaleGestureDetector scaleDetector;
    private final GestureDetector gestureDetector;

    public AutomatonView(Context context, AttributeSet attrs) {
        super(context, attrs);
        float d = getResources().getDisplayMetrics().density;

        edgePaint.setStyle(Paint.Style.STROKE);
        edgePaint.setStrokeWidth(3f * d);
        edgePaint.setColor(COLOR_EDGE);
        edgeGlowPaint.set(edgePaint);
        edgeGlowPaint.setColor(COLOR_EDGE_GLOW);
        edgeGlowPaint.setStrokeWidth(4f * d);

        arrowPaint.setStyle(Paint.Style.FILL);

        chipFill.setStyle(Paint.Style.FILL);
        chipFill.setColor(COLOR_CHIP_BG);
        chipStroke.setStyle(Paint.Style.STROKE);
        chipStroke.setStrokeWidth(1f * d);
        chipStroke.setColor(0x6600E5FF);
        chipText.setColor(COLOR_TEXT);
        chipText.setTextSize(13f * d);
        chipText.setTypeface(Typeface.DEFAULT_BOLD);
        chipText.setTextAlign(Paint.Align.CENTER);

        nodeFill.setStyle(Paint.Style.FILL);
        nodeFill.setColor(COLOR_NODE_FILL);
        nodeStroke.setStyle(Paint.Style.STROKE);
        nodeStroke.setStrokeWidth(4f * d);
        nodeStroke.setColor(COLOR_NODE_EDGE);
        acceptRing.setStyle(Paint.Style.STROKE);
        acceptRing.setStrokeWidth(2.5f * d);
        acceptRing.setColor(COLOR_ACCEPT);

        pulsePaint.setStyle(Paint.Style.STROKE);
        pulsePaint.setStrokeWidth(3f * d);
        pulsePaint.setColor(COLOR_PULSE);

        nodeLabel.setColor(COLOR_TEXT);
        nodeLabel.setTextSize(16f * d);
        nodeLabel.setTypeface(Typeface.DEFAULT_BOLD);
        nodeLabel.setTextAlign(Paint.Align.CENTER);
        nodeSub.setColor(COLOR_SUB);
        nodeSub.setTextSize(10.5f * d);
        nodeSub.setTextAlign(Paint.Align.CENTER);

        gridPaint.setStyle(Paint.Style.FILL);
        gridPaint.setColor(COLOR_GRID);

        scaleDetector = new ScaleGestureDetector(context, new ScaleListener());
        gestureDetector = new GestureDetector(context, new GestureListener());
    }

    // ------------------------------------------------------------------ public API

    /** Replace the whole graph. Everything starts hidden; use reveal* to show it. */
    public void setGraph(List<GNode> newNodes, List<GEdge> newEdges) {
        cancelAnims();
        nodes.clear(); edges.clear(); edgeByKey.clear(); nodeById.clear();
        nodes.addAll(newNodes);
        edges.addAll(newEdges);
        for (GNode n : nodes) nodeById.put(n.id, n);
        for (GEdge e : edges) edgeByKey.put(e.from + "→" + e.to, e);
        scaleFactor = 1f; tx = 0f; ty = 0f;
        nodeRadius = (nodes.size() > 9 ? 27f : 34f) * getResources().getDisplayMetrics().density;
        for (GNode n : nodes) n.radius = nodeRadius;
        requestLayout();
        invalidate();
    }

    /** Reveal one node, optionally with the pop-in animation. */
    public void revealNode(String id, boolean animate) {
        GNode n = nodeById.get(id);
        if (n == null || n.visible) return;
        n.visible = true;
        if (animate) {
            ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
            a.setDuration(420);
            a.setInterpolator(new OvershootInterpolator(1.7f));
            a.addUpdateListener(anim -> { n.appear = (float) anim.getAnimatedValue(); invalidate(); });
            a.start();
            track(a);
        } else {
            n.appear = 1f;
            invalidate();
        }
    }

    /**
     * Reveal (or reinforce) an edge for one symbol. If the edge already exists we
     * extend its label ("a" → "a,b") instead of drawing a second arrow.
     * @return true if a brand-new arrow was created.
     */
    public boolean revealEdge(String from, String to, String symbol, boolean animate) {
        GEdge e = edgeByKey.get(from + "→" + to);
        if (e == null) return false;
        if (!e.visible) {
            e.visible = true;
            e.label = symbol;
            if (animate) {
                ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
                a.setDuration(480);
                a.setInterpolator(new DecelerateInterpolator());
                a.addUpdateListener(anim -> { e.progress = (float) anim.getAnimatedValue(); invalidate(); });
                a.start();
                track(a);
                startGlow(e);
            } else {
                e.progress = 1f;
                invalidate();
            }
            return true;
        }
        if (!e.label.contains(symbol)) e.label = e.label + "," + symbol;
        startGlow(e);
        invalidate();
        return false;
    }

    /** Pulse-highlight a node (the one currently being processed). */
    public void setActiveNode(String id, boolean active) {
        GNode n = nodeById.get(id);
        if (n == null) return;
        if (active) {
            if (n.pulseAnim != null) return;
            ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
            a.setDuration(900);
            a.setRepeatCount(ValueAnimator.INFINITE);
            a.addUpdateListener(anim -> { n.pulsePhase = (float) anim.getAnimatedValue(); invalidate(); });
            a.start();
            n.pulseAnim = a;
            track(a);
        } else if (n.pulseAnim != null) {
            n.pulseAnim.cancel();
            n.pulseAnim = null;
            n.pulsePhase = 0f;
            invalidate();
        }
    }

    /** Staggered entrance for a finished diagram (NFA / final DFA tabs). */
    public void animateEntrance() {
        long delay = 0;
        for (GNode n : nodes) {
            postDelayed(() -> revealNode(n.id, true), delay);
            delay += 90;
        }
        long edgeDelay = delay + 120;
        for (GEdge e : edges) {
            postDelayed(() -> { if (e != null && !e.visible) revealEdge(e.from, e.to, e.label, true); }, edgeDelay);
            edgeDelay += 110;
        }
    }

    public void resetTransform() {
        scaleFactor = 1f; tx = 0f; ty = 0f;
        invalidate();
    }

    private void track(ValueAnimator a) {
        runningAnims.add(a);
        a.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) { runningAnims.remove(a); }
        });
    }

    private void startGlow(GEdge e) {
        if (e.glowAnim != null) e.glowAnim.cancel();
        ValueAnimator a = ValueAnimator.ofFloat(1f, 0f);
        a.setDuration(900);
        a.addUpdateListener(anim -> { e.glow = (float) anim.getAnimatedValue(); invalidate(); });
        a.start();
        e.glowAnim = a;
        track(a);
    }

    private void cancelAnims() {
        for (ValueAnimator a : new ArrayList<>(runningAnims)) a.cancel();
        runningAnims.clear();
        for (GNode n : nodes) { n.pulseAnim = null; n.pulsePhase = 0f; }
    }

    @Override protected void onDetachedFromWindow() {
        cancelAnims();
        super.onDetachedFromWindow();
    }

    // ------------------------------------------------------------------ layout

    private void layoutNodes(int w, int h) {
        if (nodes.isEmpty()) return;
        float cx = w / 2f, cy = h * 0.47f;
        float r = Math.min(w, h) / 2f - nodeRadius - dp(34);
        if (r < dp(56)) r = Math.max(dp(40), Math.min(w, h) / 2f - nodeRadius - dp(12));
        int nonTrap = countNonTrap();
        int i = 0;
        for (GNode node : nodes) {
            if (node.trap) continue;
            double angle = -Math.PI / 2 + 2 * Math.PI * i / nonTrap;
            node.x = cx + (float) (Math.cos(angle) * r);
            node.y = cy + (float) (Math.sin(angle) * r);
            i++;
        }
        for (GNode node : nodes) {
            if (node.trap) {
                node.x = cx;
                node.y = Math.min(h - nodeRadius - dp(10), cy + r + nodeRadius * 2.4f);
            }
        }
    }

    private int countNonTrap() {
        int c = 0;
        for (GNode n : nodes) if (!n.trap) c++;
        return Math.max(1, c);
    }

    // ------------------------------------------------------------------ drawing

    @Override protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        if (nodes.isEmpty() || w == 0 || h == 0) {
            drawPlaceholder(canvas, w, h);
            return;
        }
        layoutNodes(w, h);

        int save = canvas.save();
        canvas.translate(tx, ty);
        canvas.scale(scaleFactor, scaleFactor);

        drawGrid(canvas, w, h);

        // edges first, nodes on top
        for (GEdge e : edges) if (e.visible) drawEdge(canvas, e);
        for (GNode n : nodes) if (n.visible) drawNode(canvas, n);

        canvas.restoreToCount(save);
    }

    private void drawGrid(Canvas canvas, int w, int h) {
        float step = dp(38);
        float x0 = ((tx % step) + step) % step;
        float y0 = ((ty % step) + step) % step;
        float scaledStep = step * scaleFactor;
        if (scaledStep < dp(16)) return;
        for (float x = x0; x < w; x += scaledStep)
            for (float y = y0; y < h; y += scaledStep)
                canvas.drawCircle(x, y, dp(1.2f), gridPaint);
    }

    private void drawPlaceholder(Canvas canvas, int w, int h) {
        nodeSub.setTextSize(dp(13));
        nodeSub.setColor(0xFF5A6480);
        canvas.drawText("The diagram will appear here", w / 2f, h / 2f, nodeSub);
        nodeSub.setColor(COLOR_SUB);
    }

    private void drawEdge(Canvas canvas, GEdge e) {
        GNode a = nodeById.get(e.from), b = nodeById.get(e.to);
        if (a == null || b == null) return;

        edgePaint.setColor(COLOR_EDGE);
        edgeGlowPaint.setAlpha((int) (255 * e.glow));

        Path path = new Path();
        if (a == b) {
            buildSelfLoop(path, a);
        } else {
            buildArc(path, a, b);
        }

        // partial drawing via PathMeasure
        PathMeasure pm = new PathMeasure(path, false);
        float len = pm.getLength();
        float shown = len * Math.max(0.001f, e.progress);
        Path dst = new Path();
        pm.getSegment(0, shown, dst, true);
        if (e.progress > 0.999f) {
            canvas.drawPath(path, e.glow > 0.02f ? blend(edgePaint, edgeGlowPaint, e.glow) : edgePaint);
        } else {
            canvas.drawPath(dst, e.glow > 0.02f ? blend(edgePaint, edgeGlowPaint, e.glow) : edgePaint);
        }

        // arrow head rides the growing tip
        if (e.progress > 0.22f) {
            float[] pos = new float[2], tan = new float[2];
            pm.getPosTan(shown, pos, tan);
            drawArrowHead(canvas, pos[0], pos[1], tan[0], tan[1], e);
        }

        // label chip
        if (e.progress > 0.55f) {
            float alpha = Math.min(1f, (e.progress - 0.55f) / 0.45f);
            float mx, my;
            if (a == b) {
                float[] dir = outward(a);
                RectF loop = selfLoopRect(a, dir);
                mx = loop.centerX() + dir[0] * (loop.width() / 2f + dp(12));
                my = loop.centerY() + dir[1] * (loop.height() / 2f + dp(12));
            } else {
                float[] c = arcControl(a, b);
                mx = 0.25f * a.x + 0.5f * c[0] + 0.25f * b.x;
                my = 0.25f * a.y + 0.5f * c[1] + 0.25f * b.y;
            }
            drawChip(canvas, mx, my, e.label, alpha, e.glow);
        }
    }

    private Paint blend(Paint base, Paint glow, float t) {
        base.setColor(mixColor(COLOR_EDGE, COLOR_EDGE_GLOW, t));
        return base;
    }

    private static int mixColor(int c1, int c2, float t) {
        return Color.rgb(
                (int) (Color.red(c1) * (1 - t) + Color.red(c2) * t),
                (int) (Color.green(c1) * (1 - t) + Color.green(c2) * t),
                (int) (Color.blue(c1) * (1 - t) + Color.blue(c2) * t));
    }

    private void buildArc(Path path, GNode a, GNode b) {
        float[] c = arcControl(a, b);
        float[] start = borderPoint(a, c);
        float[] end = borderPoint(b, c);
        path.moveTo(start[0], start[1]);
        path.quadTo(c[0], c[1], end[0], end[1]);
    }

    /** Control point for a gently curved arc; stronger curve when the pair is bidirectional. */
    private float[] arcControl(GNode a, GNode b) {
        float dx = b.x - a.x, dy = b.y - a.y;
        float dist = (float) Math.hypot(dx, dy);
        boolean bidirectional = edgeByKey.containsKey(b.id + "→" + a.id);
        float offset = dist * (bidirectional ? 0.22f : 0.10f);
        float mx = (a.x + b.x) / 2f, my = (a.y + b.y) / 2f;
        // perpendicular unit vector
        float px = -dy / Math.max(1f, dist), py = dx / Math.max(1f, dist);
        return new float[]{mx + px * offset, my + py * offset};
    }

    /** Point on the node's circle border in the direction of target. */
    private float[] borderPoint(GNode n, float[] toward) {
        float dx = toward[0] - n.x, dy = toward[1] - n.y;
        float len = (float) Math.hypot(dx, dy);
        if (len < 0.001f) return new float[]{n.x, n.y};
        float r = n.radius();
        return new float[]{n.x + dx / len * r, n.y + dy / len * r};
    }

    private float[] outward(GNode n) {
        if (nodes.size() == 1) return new float[]{0, -1};
        float cx = getWidth() / 2f, cy = getHeight() * 0.47f;
        float dx = n.x - cx, dy = n.y - cy;
        float len = (float) Math.hypot(dx, dy);
        if (len < 0.001f) return new float[]{0, -1};
        return new float[]{dx / len, dy / len};
    }

    private RectF selfLoopRect(GNode n, float[] dir) {
        float loopR = n.radius() * 0.55f;
        float cxDist = n.radius() + loopR + dp(4);   // keep the loop fully outside the node disc
        float cx = n.x + dir[0] * cxDist, cy = n.y + dir[1] * cxDist;
        return new RectF(cx - loopR, cy - loopR, cx + loopR, cy + loopR);
    }

    private void buildSelfLoop(Path path, GNode n) {
        float[] dir = outward(n);
        RectF loop = selfLoopRect(n, dir);
        // full circle minus a small gap where it meets the node
        double base = Math.atan2(-dir[1], -dir[0]);          // direction back toward node
        float startDeg = (float) Math.toDegrees(base) + 28;
        float sweep = 360 - 56;
        path.addArc(loop, startDeg, sweep);
    }

    private void drawArrowHead(Canvas canvas, float x, float y, float txDir, float tyDir, GEdge e) {
        float len = (float) Math.hypot(txDir, tyDir);
        if (len < 0.0001f) return;
        float ux = txDir / len, uy = tyDir / len;
        float size = dp(9);
        Path tri = new Path();
        tri.moveTo(x + ux * size, y + uy * size);
        tri.lineTo(x - ux * size * 0.6f - uy * size * 0.6f, y - uy * size * 0.6f + ux * size * 0.6f);
        tri.lineTo(x - ux * size * 0.6f + uy * size * 0.6f, y - uy * size * 0.6f - ux * size * 0.6f);
        tri.close();
        arrowPaint.setColor(e.glow > 0.02f ? mixColor(COLOR_EDGE, COLOR_EDGE_GLOW, e.glow) : COLOR_EDGE);
        canvas.drawPath(tri, arrowPaint);
    }

    private void drawChip(Canvas canvas, float x, float y, String text, float alpha, float glow) {
        float tw = chipText.measureText(text);
        float padX = dp(7), padY = dp(3.5f);
        RectF r = new RectF(x - tw / 2 - padX, y - chipText.getTextSize() - padY,
                x + tw / 2 + padX, y + padY);
        chipFill.setAlpha((int) (204 * alpha));
        chipStroke.setAlpha((int) (255 * alpha));
        chipText.setAlpha((int) (255 * alpha));
        if (glow > 0.02f) chipStroke.setColor(mixColor(0x6600E5FF, 0xFF00E5FF, glow));
        canvas.drawRoundRect(r, dp(6), dp(6), chipFill);
        canvas.drawRoundRect(r, dp(6), dp(6), chipStroke);
        canvas.drawText(text, x, y, chipText);
    }

    private void drawNode(Canvas canvas, GNode n) {
        float scale = 0.25f + 0.75f * n.appear;
        float r = n.radius();
        int save = canvas.save();
        canvas.scale(scale, scale, n.x, n.y);

        // pulse ring
        if (n.pulseAnim != null) {
            float p = n.pulsePhase;
            pulsePaint.setAlpha((int) (220 * (1 - p)));
            canvas.drawCircle(n.x, n.y, r + dp(4) + p * dp(14), pulsePaint);
        }

        nodeFill.setAlpha(255);
        canvas.drawCircle(n.x, n.y, r, nodeFill);
        nodeStroke.setColor(n.trap ? COLOR_TRAP : COLOR_NODE_EDGE);
        if (n.trap) nodeStroke.setPathEffect(new DashPathEffect(new float[]{dp(6), dp(5)}, 0));
        canvas.drawCircle(n.x, n.y, r, nodeStroke);
        nodeStroke.setPathEffect(null);

        if (n.accept) {
            canvas.drawCircle(n.x, n.y, r - dp(5.5f), acceptRing);
        }
        if (n.start) {
            float[] dir = outward(n);
            double base = Math.atan2(dir[1], dir[0]) + Math.toRadians(55); // off the self-loop axis
            float[] sd = {(float) Math.cos(base), (float) Math.sin(base)};
            float sx = n.x + sd[0] * (r + dp(30)), sy = n.y + sd[1] * (r + dp(30));
            float ex = n.x + sd[0] * (r + dp(8)), ey = n.y + sd[1] * (r + dp(8));
            edgePaint.setColor(COLOR_ACCEPT);
            canvas.drawLine(sx, sy, ex, ey, edgePaint);
            arrowPaint.setColor(COLOR_ACCEPT);
            Path tri = new Path();
            tri.moveTo(ex + sd[0] * dp(4), ey + sd[1] * dp(4));
            tri.lineTo(ex - sd[1] * dp(5), ey + sd[0] * dp(5));
            tri.lineTo(ex + sd[1] * dp(5), ey - sd[0] * dp(5));
            tri.close();
            canvas.drawPath(tri, arrowPaint);
            edgePaint.setColor(COLOR_EDGE);
        }

        nodeLabel.setAlpha((int) (255 * Math.min(1f, n.appear * 1.4f)));
        canvas.drawText(n.label, n.x, n.y + nodeLabel.getTextSize() * 0.34f
                        - (n.sublabel != null ? dp(5) : 0), nodeLabel);
        if (n.sublabel != null) {
            float size = nodeSub.getTextSize();
            while (nodeSub.measureText(n.sublabel) > (r * 2 - dp(10)) && size > dp(7)) {
                size -= 0.5f;
                nodeSub.setTextSize(size);
            }
            canvas.drawText(n.sublabel, n.x, n.y + nodeLabel.getTextSize() * 0.9f + dp(2), nodeSub);
            nodeSub.setTextSize(dp(10.5f));
        }
        canvas.restoreToCount(save);
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    // ------------------------------------------------------------------ touch

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override public boolean onScale(ScaleGestureDetector d) {
            float prev = scaleFactor;
            scaleFactor = Math.max(0.4f, Math.min(3.2f, prev * d.getScaleFactor()));
            float fx = d.getFocusX(), fy = d.getFocusY();
            float ratio = scaleFactor / prev;
            tx = fx - (fx - tx) * ratio;
            ty = fy - (fy - ty) * ratio;
            invalidate();
            return true;
        }
    }

    private class GestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override public boolean onScroll(MotionEvent e1, MotionEvent e2, float dx, float dy) {
            tx -= dx; ty -= dy;
            invalidate();
            return true;
        }
        @Override public boolean onDoubleTap(MotionEvent e) {
            resetTransform();
            return true;
        }
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        boolean s = scaleDetector.onTouchEvent(event);
        boolean g = gestureDetector.onTouchEvent(event);
        return s || g || super.onTouchEvent(event);
    }
}
