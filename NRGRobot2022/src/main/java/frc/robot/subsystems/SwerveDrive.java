// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot.subsystems;

import java.util.HashMap;
import java.util.Map;

import com.ctre.phoenix.motorcontrol.ControlMode;
import com.ctre.phoenix.motorcontrol.can.TalonFX;
import com.ctre.phoenix.sensors.AbsoluteSensorRange;
import com.ctre.phoenix.sensors.CANCoder;
import com.kauailabs.navx.frc.AHRS;

import edu.wpi.first.math.MathUtil;
import edu.wpi.first.math.controller.PIDController;
import edu.wpi.first.math.controller.ProfiledPIDController;
import edu.wpi.first.math.controller.SimpleMotorFeedforward;
import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.geometry.Translation2d;
import edu.wpi.first.math.kinematics.ChassisSpeeds;
import edu.wpi.first.math.kinematics.SwerveDriveKinematics;
import edu.wpi.first.math.kinematics.SwerveDriveOdometry;
import edu.wpi.first.math.kinematics.SwerveModuleState;
import edu.wpi.first.math.trajectory.TrapezoidProfile;
import edu.wpi.first.networktables.EntryListenerFlags;
import edu.wpi.first.util.sendable.Sendable;
import edu.wpi.first.util.sendable.SendableBuilder;
import edu.wpi.first.wpilibj.RobotController;
import edu.wpi.first.wpilibj.SerialPort;
import edu.wpi.first.wpilibj.shuffleboard.BuiltInLayouts;
import edu.wpi.first.wpilibj.shuffleboard.BuiltInWidgets;
import edu.wpi.first.wpilibj.shuffleboard.Shuffleboard;
import edu.wpi.first.wpilibj.shuffleboard.ShuffleboardLayout;
import edu.wpi.first.wpilibj.shuffleboard.ShuffleboardTab;
import edu.wpi.first.wpilibj2.command.SubsystemBase;

public class SwerveDrive extends SubsystemBase {

  /*
   * 
   * Forward motion of drive wheel is the directions your fingers curl
   * when your thumb points in the direction of the bolt.
   * 
   */

  /* Swerve Module helper class */

  // Absolute position of encoders when the module wheel is pointed in the +X
  // direction
  double zeroFrontLeft = -2.197;
  double zeroFrontRight = 129.990;
  double zeroBackLeft = 88.066;
  double zeroBackRight = -70.048;

  private class Module {

    private static final double WHEEL_RADIUS = 0.047625; // Meters
    private static final int ENCODER_RESOLUTION = 2048; // Steps per Rev
    private static final double DRIVE_GEAR_RATIO = 8.14; // Gear ratio
    private static final double DRIVE_PULSES_PER_METER = (ENCODER_RESOLUTION * DRIVE_GEAR_RATIO)
        / (2 * WHEEL_RADIUS * Math.PI); // pulses per
    // meter

    private static final double MODULE_MAX_ANGULAR_VELOCITY = SwerveDrive.MAX_ANGULAR_SPEED;
    private static final double MODULE_MAX_ANGULAR_ACCELERATION = 2 * Math.PI; // radians per second squared

    private final TalonFX driveMotor;
    private final TalonFX turningMotor;

    // private final Encoder m_driveEncoder;
    private final CANCoder turningEncoder;

    /* TODO: Tune PID for drive and turning PID controllers */
    // Gains are for example purposes only - must be determined for your own robot!
    private final PIDController drivePIDController = new PIDController(1, 0, 0);

    // Gains are for example purposes only - must be determined for your own robot!
    private final ProfiledPIDController turningPIDController = new ProfiledPIDController(
        7,
        0,
        0,
        new TrapezoidProfile.Constraints(
            MODULE_MAX_ANGULAR_VELOCITY, MODULE_MAX_ANGULAR_ACCELERATION));

    // Gains are for example purposes only - must be determined for your own robot!
    private final SimpleMotorFeedforward driveFeedforward = new SimpleMotorFeedforward(1, 3);
    private final SimpleMotorFeedforward turnFeedforward = new SimpleMotorFeedforward(1, 0.5);

    private SwerveModuleState desiredState = new SwerveModuleState(0, new Rotation2d(0));

    private double turnPIDOutput = 0;
    private double turnFeedForwardOutput = 0;

    private String moduleName;

    /**
     * Constructs a SwerveModule with a drive motor, turning motor, drive encoder
     * and turning encoder.
     *
     * @param driveMotorChannel    CAN ID of the drive motor.
     * @param turningMotorChannel  CAN ID of the turning motor.
     * @param turningEncodeChannel CAN ID of the turning encoder
     */
    public Module(int driveMotorChannel, int turningMotorChannel, int turningEncodeChannel, String moduleName) {
      driveMotor = new TalonFX(driveMotorChannel);
      turningMotor = new TalonFX(turningMotorChannel);

      turningEncoder = new CANCoder(turningEncodeChannel);

      this.moduleName = moduleName;

      turningEncoder.configAbsoluteSensorRange(AbsoluteSensorRange.Signed_PlusMinus180);
      // m_turningEncoder.config
      // Limit the PID Controller's input range between -pi and pi and set the input
      // to be continuous.
      turningPIDController.enableContinuousInput(-Math.PI, Math.PI);
      turningPIDController.reset(Math.toRadians(turningEncoder.getAbsolutePosition()));

    }

    /** Resets the module. */
    public void reset() {
      stopMotor();
      turningPIDController.reset(Math.toRadians(turningEncoder.getAbsolutePosition()));
    }

    /** Returns the current state of the module. */
    public SwerveModuleState getState() {
      return new SwerveModuleState(getWheelVelocity(),
          Rotation2d.fromDegrees(turningEncoder.getAbsolutePosition()));
    }

    /** Returns wheel velocity in meters per second. */
    public double getWheelVelocity() {
      // talonFX reports velocity in pulses per 100ms; multiply by 10 to convert to
      // seconds
      return (driveMotor.getSelectedSensorVelocity() * 10) / DRIVE_PULSES_PER_METER;
    }

    /** Returns the distance the wheel has travelled in meters. */
    public double getWheelDistance() {
      return driveMotor.getSelectedSensorPosition() / DRIVE_PULSES_PER_METER;
    }

    /** Returns the module state set by the last call to setDesiredState. */
    public SwerveModuleState getDesiredState() {
      return desiredState;
    }

    /**
     * Sets the desired state for the module.
     *
     * @param desiredState Desired state with speed and angle.
     */
    public void setDesiredState(SwerveModuleState desiredState) {
      // Optimize the reference state to avoid spinning further than 90 degrees

      this.desiredState = desiredState;
      Rotation2d currentAngle = Rotation2d.fromDegrees(turningEncoder.getAbsolutePosition());
      SwerveModuleState state = SwerveModuleState.optimize(desiredState, currentAngle);

      // Calculate the drive output from the drive PID controller.
      final double driveOutput = drivePIDController.calculate(getWheelVelocity(), state.speedMetersPerSecond);

      final double driveFeedforward = this.driveFeedforward.calculate(state.speedMetersPerSecond);

      // Calculate the turning motor output from the turning PID controller.
      final double turnOutput = turningPIDController.calculate(currentAngle.getRadians(), state.angle.getRadians());

      final double turnFeedforward = this.turnFeedforward.calculate(turningPIDController.getSetpoint().velocity);
      final double batteryVolatage = RobotController.getBatteryVoltage();

      turnFeedForwardOutput = turnFeedforward;
      turnPIDOutput = turnOutput;

      driveMotor.set(ControlMode.PercentOutput, (driveOutput + driveFeedforward) / batteryVolatage);
      turningMotor.set(ControlMode.PercentOutput, (turnOutput + turnFeedforward) / batteryVolatage);
    }

    /** Stops the drive and turn motors */
    public void stopMotors() {
      driveMotor.set(ControlMode.PercentOutput, 0);
      turningMotor.set(ControlMode.PercentOutput, 0);

    }

    public double getFeedForwardOutput() {
      return turnFeedForwardOutput;
    }

    public double getTurnPIDOutput() {
      return turnPIDOutput;
    }

    public double getWheelAngle() {
      return turningEncoder.getAbsolutePosition();
    }

    public double getRelativePosition() {
      return turningEncoder.getPosition();
    }

    public void setDriveMotorPower(double power) {
      driveMotor.set(ControlMode.PercentOutput, power);
    }

    public void setTurnMotorPower(double power) {
      turningMotor.set(ControlMode.PercentOutput, power);

    }

    public ShuffleboardLayout addShuffleBoardLayout(ShuffleboardTab tab) {
      ShuffleboardLayout layout = tab.getLayout(moduleName, BuiltInLayouts.kList);

      layout.add("Drive Motor", 0)
          .withWidget(BuiltInWidgets.kNumberSlider)
          .getEntry()
          .addListener(
              (event) -> this.setDriveMotorPower(event.getEntry().getDouble(0)),
              EntryListenerFlags.kNew | EntryListenerFlags.kUpdate);

      layout.add("Turn Motor", 0)
          .withWidget(BuiltInWidgets.kNumberSlider)
          .getEntry()
          .addListener(
              (event) -> this.setTurnMotorPower(event.getEntry().getDouble(0)),
              EntryListenerFlags.kNew | EntryListenerFlags.kUpdate);

      layout.add("Rotation", new Sendable() {

        @Override
        public void initSendable(SendableBuilder builder) {
          builder.setSmartDashboardType("Gyro");
          builder.addDoubleProperty("Value", () -> getWheelAngle(), null);     
        }
        
      }).withWidget(BuiltInWidgets.kGyro).withPosition(0, 0);

      return layout;
    }
  }

  public static final double MAX_SPEED = 3.0; // 3 meters per second
  public static final double MAX_ANGULAR_SPEED = Math.PI; // 1/2 rotation per second
  public static final double MAX_ACCELERATION = 2.0; // TODO: find Max acceleration in meters per second squared

  public static double currentMaxSpeed = MAX_SPEED;
  public static double currentMaxAngularSpeed = MAX_ANGULAR_SPEED;

  // X and Y swaped
  private final Translation2d m_frontLeftLocation = new Translation2d(0.34925, 0.24765);
  private final Translation2d m_frontRightLocation = new Translation2d(0.34925, -0.24765);
  private final Translation2d m_backLeftLocation = new Translation2d(-0.34925, 0.24765);
  private final Translation2d m_backRightLocation = new Translation2d(-0.34925, -0.24765);

  private final Module m_frontLeft = new Module(1, 2, 9, "Front Left");
  private final Module m_frontRight = new Module(3, 4, 10, "Front Right");
  private final Module m_backLeft = new Module(7, 8, 12, "Back Left");
  private final Module m_backRight = new Module(5, 6, 11, "Back Right");

  private final AHRS m_ahrs = new AHRS(SerialPort.Port.kMXP);

  public final SwerveDriveKinematics m_kinematics = new SwerveDriveKinematics(
      m_frontLeftLocation, m_frontRightLocation, m_backLeftLocation, m_backRightLocation);

  private final SwerveDriveOdometry m_odometry = new SwerveDriveOdometry(m_kinematics, getRotation2d());

  public SwerveDrive() {
    m_ahrs.reset();
  }

  public void reset() {
    m_ahrs.reset();
    m_frontLeft.reset();
    m_frontRight.reset();
    m_backLeft.reset();
    m_backRight.reset();
    m_odometry.resetPosition(new Pose2d(), getRotation2d());
  }

  /**
   * Method to drive the robot using joystick info.
   *
   * @param xSpeed        Speed of the robot in the x direction (forward).
   * @param ySpeed        Speed of the robot in the y direction (sideways).
   * @param rot           Angular rate of the robot.
   * @param fieldRelative Whether the provided x and y speeds are relative to the
   *                      field.
   */
  @SuppressWarnings("ParameterName")
  public void drive(double xSpeed, double ySpeed, double rot, boolean fieldRelative) {
    xSpeed = MathUtil.applyDeadband(xSpeed, 0.02) * currentMaxSpeed;
    ySpeed = MathUtil.applyDeadband(ySpeed, 0.02) * currentMaxSpeed;
    rot = MathUtil.applyDeadband(rot, 0.02) * currentMaxAngularSpeed;

    var swerveModuleStates = m_kinematics.toSwerveModuleStates(
        fieldRelative
            ? ChassisSpeeds.fromFieldRelativeSpeeds(xSpeed, ySpeed, rot, getRotation2d())
            : new ChassisSpeeds(xSpeed, ySpeed, rot));
    setModuleStates(swerveModuleStates);
  }

  public void setMaxSpeed(double speed) {
    currentMaxSpeed = MathUtil.clamp(speed, 0, MAX_SPEED);

  }

  public void setMaxAngularSpeed(double angularSpeed) {
    currentMaxAngularSpeed = MathUtil.clamp(angularSpeed, 0, MAX_ANGULAR_SPEED);
  }

  /** Updates the field relative position of the robot. */
  public void updateOdometry() {
    m_odometry.update(
        getRotation2d(),
        m_frontLeft.getState(),
        m_frontRight.getState(),
        m_backLeft.getState(),
        m_backRight.getState());
  }

  /**
   * Resets the odometry to the specified pose.
   *
   * @param pose The pose to which to set the odometry.
   */
  public void resetOdometry(Pose2d pose) {
    m_odometry.resetPosition(pose, getRotation2d());
  }

  @Override
  public void periodic() {
    updateOdometry();
  }

  /** Returns the current orientation of the robot as a Rotation2d object */
  public Rotation2d getRotation2d() {
    return Rotation2d.fromDegrees(-m_ahrs.getAngle());
  }

  /** Returns the current pose of the robot as a Pose2d object */
  public Pose2d getPose2d() {
    return m_odometry.getPoseMeters();
  }

  // Get the Absolute Turning Encoder Position of a Swerve Module
  public double getAbsoluteTurningEncoderPosition(int index) {
    switch (index) {
      case 0:
        return m_frontLeft.getWheelAngle();
      case 1:
        return m_frontRight.getWheelAngle();
      case 2:
        return m_backLeft.getWheelAngle();
      case 3:
        return m_backRight.getWheelAngle();
    }
    return 0;
  }

  // Get the Relative Turning Encoder Position of a Swerve Module
  public double getRelativeTurningEncoderPosition(int index) {
    switch (index) {
      case 0:
        return m_frontLeft.getRelativePosition();
      case 1:
        return m_frontRight.getRelativePosition();
      case 2:
        return m_backLeft.getRelativePosition();
      case 3:
        return m_backRight.getRelativePosition();
    }
    return 0;
  }

  // Sets the Swerve Module State of a Swerve Module
  public void setModuleState(int index, double speed, double angle) {
    Rotation2d rotation = Rotation2d.fromDegrees(angle);
    SwerveModuleState state = new SwerveModuleState(speed, rotation);
    switch (index) {
      case 0:
        m_frontLeft.setDesiredState(state);
        break;
      case 1:
        m_frontRight.setDesiredState(state);
        break;
      case 2:
        m_backLeft.setDesiredState(state);
        break;
      case 3:
        m_backRight.setDesiredState(state);
        break;
    }

  }

  public void setModuleStates(SwerveModuleState[] desiredStates) {
    SwerveDriveKinematics.desaturateWheelSpeeds(
        desiredStates, MAX_SPEED);
    m_frontLeft.setDesiredState(desiredStates[0]);
    m_frontRight.setDesiredState(desiredStates[1]);
    m_backLeft.setDesiredState(desiredStates[2]);
    m_backRight.setDesiredState(desiredStates[3]);
  }

  // Stops all Swerve Drive Motors
  public void stopMotor() {
    m_frontLeft.stopMotors();
    m_frontRight.stopMotors();
    m_backLeft.stopMotors();
    m_backRight.stopMotors();

  }

  public void initShuffleboardTab() {
    ShuffleboardTab swerveDriveTab = Shuffleboard.getTab("Swerve Drive");

    ShuffleboardLayout swerveOdometry = swerveDriveTab.getLayout("Odometry", BuiltInLayouts.kGrid)
        .withPosition(0, 0)
        .withSize(2, 3);

    swerveOdometry.addNumber("Gyro", () -> getRotation2d().getDegrees());
    swerveOdometry.addNumber("X", () -> getPose2d().getX());
    swerveOdometry.addNumber("Y", () -> getPose2d().getY());
    swerveOdometry.addNumber("FR Encoder", () -> m_frontRight.getWheelDistance());
    swerveOdometry.addNumber("FL Encoder", () -> m_frontLeft.getWheelDistance());
    swerveOdometry.addNumber("BR Encoder", () -> m_backRight.getWheelDistance());
    swerveOdometry.addNumber("BL Encoder", () -> m_backLeft.getWheelDistance());

    m_frontLeft.addShuffleBoardLayout(swerveDriveTab)
        .withPosition(2, 0)
        .withSize(2, 3);

    m_frontRight.addShuffleBoardLayout(swerveDriveTab)
        .withPosition(4, 0)
        .withSize(2, 3);

    m_backLeft.addShuffleBoardLayout(swerveDriveTab)
        .withPosition(2, 3)
        .withSize(2, 3);

    m_backRight.addShuffleBoardLayout(swerveDriveTab)
        .withPosition(4, 3)
        .withSize(2, 3);

    ShuffleboardLayout virtualGearBox = swerveDriveTab.getLayout("Swerve Speed Controller", BuiltInLayouts.kGrid)
        .withPosition(0, 3)
        .withSize(2, 2);

    Map<String, Object> maxSpeedSliderProperties = new HashMap<>();
    maxSpeedSliderProperties.put("Min", 0);
    maxSpeedSliderProperties.put("Max", MAX_SPEED);

    virtualGearBox.add("Max Speed", 0)
        .withWidget(BuiltInWidgets.kNumberSlider)
        .withProperties(maxSpeedSliderProperties)
        .withPosition(0, 0)
        .getEntry()
        .addListener(
            (event) -> setMaxSpeed(event.getEntry().getDouble(MAX_SPEED)),
            EntryListenerFlags.kNew | EntryListenerFlags.kUpdate);

    Map<String, Object> maxAngularSpeedSliderProperties = new HashMap<>();
    maxAngularSpeedSliderProperties.put("Min", 0);
    maxAngularSpeedSliderProperties.put("Max", MAX_ANGULAR_SPEED);

    virtualGearBox.add("Max Angular Speed", 0)
        .withWidget(BuiltInWidgets.kNumberSlider)
        .withProperties(maxAngularSpeedSliderProperties)
        .withPosition(1, 0)
        .getEntry()
        .addListener(
            (event) -> setMaxAngularSpeed(event.getEntry().getDouble(MAX_ANGULAR_SPEED)),
            EntryListenerFlags.kNew | EntryListenerFlags.kUpdate);

  }
}
