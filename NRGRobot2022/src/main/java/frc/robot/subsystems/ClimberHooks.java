package frc.robot.subsystems;

import edu.wpi.first.wpilibj.DigitalInput;
import edu.wpi.first.wpilibj.DoubleSolenoid;
import edu.wpi.first.wpilibj.PneumaticsModuleType;
import edu.wpi.first.wpilibj.DoubleSolenoid.Value;
import edu.wpi.first.wpilibj2.command.SubsystemBase;
// import frc.robot.preferences.RobotPreferencesLayout;

public class ClimberHooks extends SubsystemBase {
    
    private final DoubleSolenoid piston1;
    private final DoubleSolenoid piston2;
    private final DigitalInput beamBreak1;
    private final DigitalInput beamBreak2;

    public enum State {
        OPEN, // kForward position
        CLOSED; // kReverse position
    }

    public enum HookSelection {
        HOOK_1, HOOK_2;
    }

    /** Creates a new ClimberHooks subsystem. **/
    public ClimberHooks() {
        piston1 = new DoubleSolenoid(PneumaticsModuleType.CTREPCM, 2, 3);
        piston2 = new DoubleSolenoid(PneumaticsModuleType.CTREPCM, 4, 5);

        // The beam breaks will read TBD(true/false) when it engages the bar
        beamBreak1 = new DigitalInput(5);
        beamBreak2 = new DigitalInput(6);
    }

    @Override
    public void periodic() {
        // This method will be called once per scheduler run
    }

    @Override
    public void simulationPeriodic() {
        // This method will be called once per scheduler run during simulation
    }

    

    /** Returns true iff the climber hook is latched on a bar. */
    public boolean isHookLatched(HookSelection hook) {
        if (hook.equals(HookSelection.HOOK_1)) {
            return beamBreak1.get();
        } else {
            return beamBreak2.get();
        }
    }

    /** Returns the current state of a climber hook piston. */
    public State getState(HookSelection hook) {
        return getPiston(hook).get() == Value.kForward ? State.OPEN : State.CLOSED;
    }

    /** Sets the state of a climber hook piston. */
    public void setState(State state, HookSelection hook) {
        getPiston(hook).set(state == State.OPEN ? Value.kForward : Value.kReverse);
    }

    /** Reverses the state of a climber hook piston. */
    public void toggleState(HookSelection hook) {
        setState(getState(hook) == State.OPEN ? State.CLOSED : State.OPEN, hook);
    }

    private DoubleSolenoid getPiston(HookSelection hook) {
        if (hook.equals(HookSelection.HOOK_1)) {
            return piston1;
        } else {
            return piston2;
        }
    }
    /*
  CommandSequence:
  1: 
  P1: Retracted
  P2: Extended
  Hits first bar/(Hits Limit switch 1): 
    Retract P2
  2: 
  P1: Retracted
  P2: Retracted
  Hits second bar (Hits Limit switch 2): 
    Extend P1
    Retract P1
  3: 
  P1: Retracted
  P2: Retracted
  Hits Traveral Bar (Hits Limit switch 1): 
    Extend P2
  Stop Motor. 
  */
}