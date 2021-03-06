/*
 * Copyright (c) 2016, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.nodes.impl.base;

import java.util.List;
import java.util.Map;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.llvm.nodes.base.LLVMNode;
import com.oracle.truffle.llvm.parser.NodeFactoryFacade;
import com.oracle.truffle.llvm.runtime.LLVMOptimizationConfiguration;
import com.oracle.truffle.llvm.types.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.types.LLVMFunctionDescriptor.LLVMRuntimeType;

/**
 * Manages Sulong functions and intrinsified native functions.
 */
public class LLVMFunctionRegistry {

    private static final String ZERO_FUNCTION = "<zero function>";

    // do not start with 0, otherwise the first function
    // pointer would be == NULL
    private static final int REAL_FUNCTION_START_INDEX = 1;

    private final Map<String, NodeFactory<? extends LLVMNode>> intrinsics;
    private final NodeFactoryFacade facade;

    /**
     * The function index assigned to the next function descriptor.
     */
    private int currentFunctionIndex = REAL_FUNCTION_START_INDEX;

    /**
     * Maps a function index (see {@link LLVMFunctionDescriptor#getFunctionIndex()} to a call
     * target.
     */
    @CompilationFinal private RootCallTarget[] functionPtrCallTargetMap;

    /**
     * Maps a function index (see {@link LLVMFunctionDescriptor#getFunctionIndex()} to a function
     * descriptor.
     */
    @CompilationFinal private LLVMFunctionDescriptor[] functionDescriptors = new LLVMFunctionDescriptor[REAL_FUNCTION_START_INDEX];

    public LLVMFunctionRegistry(LLVMOptimizationConfiguration optimizationConfig, NodeFactoryFacade facade) {
        this.facade = facade;
        this.intrinsics = facade.getFunctionSubstitutionFactories(optimizationConfig);
        functionPtrCallTargetMap = new RootCallTarget[REAL_FUNCTION_START_INDEX + intrinsics.size() + 1];
        functionDescriptors[0] = LLVMFunctionDescriptor.create(ZERO_FUNCTION, LLVMRuntimeType.ILLEGAL, new LLVMRuntimeType[0], false, 0);
        registerIntrinsics();
    }

    /**
     * Looks up the call target for a specific function. The lookup may return <code>null</code> if
     * the function is a native function or if the function cannot be found.
     *
     * @param function the function
     * @return the call target, <code>null</code> if not found.
     */
    public RootCallTarget lookup(LLVMFunctionDescriptor function) {
        int functionIndex = function.getFunctionIndex();
        if (functionIndex >= 0 && functionIndex < functionPtrCallTargetMap.length) {
            RootCallTarget result = functionPtrCallTargetMap[functionIndex];
            return result;
        } else {
            return null;
        }
    }

    public void register(Map<LLVMFunctionDescriptor, RootCallTarget> functionCallTargets) {
        CompilerAsserts.neverPartOfCompilation();
        int maxFunctionIndex = Math.max(maxIndex(functionCallTargets) + 1, functionPtrCallTargetMap.length);
        RootCallTarget[] newFunctionPtrCallTargetMap = new RootCallTarget[maxFunctionIndex];
        System.arraycopy(functionPtrCallTargetMap, 0, newFunctionPtrCallTargetMap, 0, functionPtrCallTargetMap.length);
        for (LLVMFunctionDescriptor func : functionCallTargets.keySet()) {
            newFunctionPtrCallTargetMap[func.getFunctionIndex()] = functionCallTargets.get(func);
        }
        functionPtrCallTargetMap = newFunctionPtrCallTargetMap;
    }

    private static int maxIndex(Map<LLVMFunctionDescriptor, RootCallTarget> functionCallTargets) {
        int maxIndex = 0;
        for (LLVMFunctionDescriptor descr : functionCallTargets.keySet()) {
            maxIndex = Math.max(maxIndex, descr.getFunctionIndex());
        }
        return maxIndex;
    }

    private void registerIntrinsics() {
        for (String intrinsicFunction : intrinsics.keySet()) {
            LLVMFunctionDescriptor function = createFunctionDescriptor(intrinsicFunction, LLVMRuntimeType.ILLEGAL, new LLVMRuntimeType[0], false);
            NodeFactory<? extends LLVMNode> nodeFactory = intrinsics.get(intrinsicFunction);
            List<Class<? extends Node>> executionSignature = nodeFactory.getExecutionSignature();
            int nrArguments = executionSignature.size();
            LLVMNode[] args = new LLVMNode[nrArguments];
            for (int i = 0; i < nrArguments; i++) {
                args[i] = facade.createFunctionArgNode(i, executionSignature.get(i));
            }
            LLVMNode intrinsicNode = nodeFactory.createNode((Object[]) args);
            RootNode functionRoot = facade.createFunctionSubstitutionRootNode(intrinsicNode);
            RootCallTarget callTarget = Truffle.getRuntime().createCallTarget(functionRoot);
            assert functionPtrCallTargetMap[function.getFunctionIndex()] == null;
            functionPtrCallTargetMap[function.getFunctionIndex()] = callTarget;
        }
    }

    /**
     * Creates an unique function descriptor identified by the given <code>name</code>.
     *
     * @param name the function's name
     * @param returnType the function's return type
     * @param paramTypes the function's
     * @param varArgs
     * @return the function descriptor
     */
    public LLVMFunctionDescriptor createFunctionDescriptor(String name, LLVMRuntimeType returnType, LLVMRuntimeType[] paramTypes, boolean varArgs) {
        CompilerAsserts.neverPartOfCompilation();
        for (int i = 0; i < functionDescriptors.length; i++) {
            if (functionDescriptors[i].getName().equals(name)) {
                return functionDescriptors[i];
            }
        }
        LLVMFunctionDescriptor function = LLVMFunctionDescriptor.create(name, returnType, paramTypes, varArgs, currentFunctionIndex++);
        LLVMFunctionDescriptor[] newFunctions = new LLVMFunctionDescriptor[functionDescriptors.length + 1];
        System.arraycopy(functionDescriptors, 0, newFunctions, 0, functionDescriptors.length);
        newFunctions[function.getFunctionIndex()] = function;
        functionDescriptors = newFunctions;
        return function;
    }

    /**
     * Creates a function descriptor from the given <code>index</code> that has previously been
     * obtained by {@link LLVMFunctionDescriptor#getFunctionIndex()} .
     *
     * @param index the function index
     * @return the function descriptor
     */
    public LLVMFunctionDescriptor createFromIndex(int index) {
        LLVMFunctionDescriptor llvmFunction = LLVMFunctionDescriptor.create(index);
        assert llvmFunction != null;
        return llvmFunction;
    }

    public LLVMFunctionDescriptor[] getFunctionDescriptors() {
        return functionDescriptors;
    }

    public boolean isZeroFunctionDescriptor(LLVMFunctionDescriptor function) {
        return function.getName().equals(ZERO_FUNCTION);
    }

    public LLVMFunctionDescriptor createZeroFunctionDescriptor() {
        return createFunctionDescriptor(ZERO_FUNCTION, LLVMRuntimeType.ILLEGAL, new LLVMRuntimeType[0], false);
    }

}
