package com.oracle.truffle.llvm.nodes.impl.intrinsics.c;

import com.oracle.graal.truffle.OptimizedCallTarget;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.llvm.nodes.impl.base.LLVMContext;
import com.oracle.truffle.llvm.nodes.impl.base.LLVMFunctionNode;
import com.oracle.truffle.llvm.nodes.impl.base.LLVMLanguage;
import com.oracle.truffle.llvm.nodes.impl.func.LLVMCallAtExitNode;
import com.oracle.truffle.llvm.nodes.impl.intrinsics.llvm.LLVMIntrinsic.LLVMI32Intrinsic;
import com.oracle.truffle.llvm.types.LLVMFunctionDescriptor;

/**
 * Intrinsic for executing a given Function at exit
 *
 */
@NodeChildren({@NodeChild(type = LLVMFunctionNode.class)})
public abstract class LLVMAtExit extends LLVMI32Intrinsic {

    @Specialization
    public int executeInt(final VirtualFrame frame, final LLVMFunctionDescriptor descriptor) {
        LLVMContext context = LLVMLanguage.INSTANCE.findContext0(LLVMLanguage.INSTANCE.createFindContextNode0());
        OptimizedCallTarget callTarget = (OptimizedCallTarget) context.getFunction(descriptor);
        LLVMCallAtExitNode target = new LLVMCallAtExitNode(callTarget, frame);
        context.registerAtExitNode(target);
        return 0;
    }
}
