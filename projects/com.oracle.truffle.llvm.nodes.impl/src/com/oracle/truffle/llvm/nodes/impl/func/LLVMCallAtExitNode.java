package com.oracle.truffle.llvm.nodes.impl.func;

import com.oracle.graal.truffle.OptimizedCallTarget;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.MaterializedFrame;

public class LLVMCallAtExitNode implements CallTarget {

    private final OptimizedCallTarget toExecute;
    private final MaterializedFrame frame;

    public LLVMCallAtExitNode(OptimizedCallTarget toExecute, Frame frame) {
        this.toExecute = toExecute;
        this.frame = frame.materialize();
    }

    @Override
    public Object call(Object... arguments) {
        return toExecute.call(frame);
    }

}
