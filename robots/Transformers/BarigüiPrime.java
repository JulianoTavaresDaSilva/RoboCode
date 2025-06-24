package Transformers;

import robocode.*;
import robocode.util.Utils;
import java.awt.geom.*;
import java.util.ArrayList;

public class BarigüiPrime extends AdvancedRobot {

    private Point2D.Double myLocation;
    private Point2D.Double enemyLocation;
    private Rectangle2D.Double fieldRect;

    private ArrayList<EnemyWave> enemyWaves = new ArrayList<>();
    private ArrayList<Integer> surfDirections = new ArrayList<>();
    private ArrayList<Double> surfAbsBearings = new ArrayList<>();
    private static final int BINS = 47;
    private static final double[] surfStats = new double[BINS];
    private static final double WALL_STICK = 160;

    private double opponentEnergy = 100;

    public void run() {
        fieldRect = new Rectangle2D.Double(18, 18, getBattleFieldWidth() - 36, getBattleFieldHeight() - 36);
        enemyWaves = new ArrayList<>();
        surfDirections = new ArrayList<>();
        surfAbsBearings = new ArrayList<>();
        setAdjustGunForRobotTurn(true);
        setAdjustRadarForGunTurn(true);

        while (true) {
            turnRadarRightRadians(Double.POSITIVE_INFINITY);
        }
    }

    public void onScannedRobot(ScannedRobotEvent e) {
        myLocation = new Point2D.Double(getX(), getY());
        double absBearing = getHeadingRadians() + e.getBearingRadians();
        enemyLocation = project(myLocation, absBearing, e.getDistance());
        setTurnRadarRightRadians(Utils.normalRelativeAngle(absBearing - getRadarHeadingRadians()) * 2);

        double lateralVelocity = getVelocity() * Math.sin(e.getBearingRadians());
        surfDirections.add(0, lateralVelocity >= 0 ? 1 : -1);
        surfAbsBearings.add(0, absBearing + Math.PI);

        double bulletPower = opponentEnergy - e.getEnergy();
        if (bulletPower > 0.09 && bulletPower <= 3.0 && surfDirections.size() > 2) {
            EnemyWave ew = new EnemyWave();
            ew.fireTime = getTime() - 1;
            ew.bulletVelocity = 20.0 - 3.0 * bulletPower;
            ew.distanceTraveled = ew.bulletVelocity;
            ew.direction = surfDirections.get(2);
            ew.directAngle = surfAbsBearings.get(2);
            ew.fireLocation = (Point2D.Double) enemyLocation.clone();
            enemyWaves.add(ew);
        }

        opponentEnergy = e.getEnergy();
        updateWaves();
        doSurfing();
        fireAtEnemy(e);
    }

    public void onHitByBullet(HitByBulletEvent e) {
        back(10);
    }

    public void onHitWall(HitWallEvent e) {
        back(20);
    }

    private void updateWaves() {
        for (int i = 0; i < enemyWaves.size(); i++) {
            EnemyWave ew = enemyWaves.get(i);
            ew.distanceTraveled = (getTime() - ew.fireTime) * ew.bulletVelocity;
            if (ew.distanceTraveled > myLocation.distance(ew.fireLocation) + 50) {
                enemyWaves.remove(i--);
            }
        }
    }

    private void fireAtEnemy(ScannedRobotEvent e) {
        double distance = e.getDistance();
        double bulletPower = (distance <= 150) ? 3.0 : (distance <= 400) ? 2.0 : 1.0;
        double bulletSpeed = 20.0 - 3.0 * bulletPower;

        Point2D.Double predicted = (Point2D.Double) enemyLocation.clone();
        double heading = e.getHeadingRadians();
        double velocity = e.getVelocity();
        double time = 0;

        while ((++time) * bulletSpeed < myLocation.distance(predicted)) {
            predicted = project(predicted, heading, velocity);
            if (!fieldRect.contains(predicted)) heading += Math.PI;
        }

        double absBearing = absoluteBearing(myLocation, predicted);
        double gunTurn = Utils.normalRelativeAngle(absBearing - getGunHeadingRadians());

        setTurnGunRightRadians(gunTurn);
        if (getGunHeat() == 0 && Math.abs(gunTurn) < Math.PI / 18) {
            setFire(bulletPower);
        }
    }

    private void doSurfing() {
        EnemyWave surfWave = getClosestSurfableWave();
        if (surfWave == null) return;

        double dangerLeft = checkDanger(surfWave, -1);
        double dangerRight = checkDanger(surfWave, 1);

        double goAngle = absoluteBearing(surfWave.fireLocation, myLocation);
        goAngle = (dangerLeft < dangerRight)
                ? wallSmoothing(myLocation, goAngle - Math.PI / 2, -1)
                : wallSmoothing(myLocation, goAngle + Math.PI / 2, 1);

        setBackAsFront(this, goAngle);
    }

    private EnemyWave getClosestSurfableWave() {
        EnemyWave closest = null;
        double closestDist = Double.POSITIVE_INFINITY;

        for (EnemyWave ew : enemyWaves) {
            double distance = myLocation.distance(ew.fireLocation) - ew.distanceTraveled;
            if (distance > ew.bulletVelocity && distance < closestDist) {
                closest = ew;
                closestDist = distance;
            }
        }
        return closest;
    }

    private double checkDanger(EnemyWave wave, int direction) {
        int index = getFactorIndex(wave, predictPosition(wave, direction));
        return surfStats[index];
    }

    private Point2D.Double predictPosition(EnemyWave wave, int direction) {
        Point2D.Double position = (Point2D.Double) myLocation.clone();
        double velocity = getVelocity();
        double heading = getHeadingRadians();
        int counter = 0;

        while (counter++ < 500) {
            double moveAngle = wallSmoothing(position, absoluteBearing(wave.fireLocation, position) + direction * Math.PI / 2, direction) - heading;
            double moveDir = Math.cos(moveAngle) >= 0 ? 1 : -1;

            if (moveDir == -1) moveAngle += Math.PI;
            moveAngle = Utils.normalRelativeAngle(moveAngle);

            double maxTurn = Math.PI / 720 * (40 - 3 * Math.abs(velocity));
            heading = Utils.normalRelativeAngle(heading + limit(-maxTurn, moveAngle, maxTurn));

            velocity += (velocity * moveDir < 0 ? 2 * moveDir : moveDir);
            velocity = limit(-8, velocity, 8);

            position = project(position, heading, velocity);

            if (position.distance(wave.fireLocation) < wave.distanceTraveled + (counter * wave.bulletVelocity) + wave.bulletVelocity)
                break;
        }

        return position;
    }

    private int getFactorIndex(EnemyWave wave, Point2D.Double target) {
        double offsetAngle = absoluteBearing(wave.fireLocation, target) - wave.directAngle;
        double factor = Utils.normalRelativeAngle(offsetAngle) / maxEscapeAngle(wave.bulletVelocity) * wave.direction;
        return (int) limit(0, (factor * ((BINS - 1) / 2)) + ((BINS - 1) / 2), BINS - 1);
    }

    private double wallSmoothing(Point2D.Double pos, double angle, int orientation) {
        while (!fieldRect.contains(project(pos, angle, WALL_STICK))) {
            angle += orientation * 0.05;
        }
        return angle;
    }

    private static Point2D.Double project(Point2D.Double source, double angle, double length) {
        return new Point2D.Double(source.x + Math.sin(angle) * length, source.y + Math.cos(angle) * length);
    }

    private static double absoluteBearing(Point2D.Double source, Point2D.Double target) {
        return Math.atan2(target.x - source.x, target.y - source.y);
    }

    private static double limit(double min, double val, double max) {
        return Math.max(min, Math.min(val, max));
    }

    private static double maxEscapeAngle(double velocity) {
        return Math.asin(8.0 / velocity);
    }

    private static void setBackAsFront(AdvancedRobot robot, double angle) {
        double turn = Utils.normalRelativeAngle(angle - robot.getHeadingRadians());
        if (Math.abs(turn) > Math.PI / 2) {
            robot.setTurnRightRadians(Utils.normalRelativeAngle(turn + Math.PI));
            robot.setBack(100);
        } else {
            robot.setTurnRightRadians(turn);
            robot.setAhead(100);
        }
    }

    private static class EnemyWave {
        Point2D.Double fireLocation;
        long fireTime;
        double bulletVelocity;
        double directAngle;
        double distanceTraveled;
        int direction;
    }
}
