// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot.commands;

import edu.wpi.first.wpilibj.XboxController;
import edu.wpi.first.wpilibj2.command.CommandBase;
import frc.robot.subsystems.ClimberRotator;

public class ManualClimber extends CommandBase {

  private ClimberRotator climberRotator;
  private XboxController controller;

  /** Creates a new ManualClimber. */
  public ManualClimber(ClimberRotator climberRotator, XboxController controller) {
    this.climberRotator = climberRotator;
    this.controller = controller;

    addRequirements(this.climberRotator);
  }

  // Called when the command is initially scheduled.
  @Override
  public void initialize() {}

  // Called every time the scheduler runs while the command is scheduled.
  @Override
  public void execute() {
    climberRotator.rotateMotor(controller.getRightY() >= 0 ? controller.getRightY() : 0);
  }

  // Called once the command ends or is interrupted.
  @Override
  public void end(boolean interrupted) {}

  // Returns true when the command should end.
  @Override
  public boolean isFinished() {
    return false;
  }
}
