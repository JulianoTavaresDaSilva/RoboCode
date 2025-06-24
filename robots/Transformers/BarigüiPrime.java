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

    private Point2D.Double project(Point2D.Double sourceLocation, double angle, double length) {
        return new Point2D.Double(sourceLocation.getX() + Math.sin(angle) * length,
                                  sourceLocation.getY() + Math.cos(angle) * length);
    }

    private double absoluteBearing(Point2D.Double source, Point2D.Double target) {
        return Math.atan2(target.getX() - source.getX(), target.getY() - source.getY());
    }

    private void doSurfing() {

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