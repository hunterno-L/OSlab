import { CPU_ALGORITHMS, validateCpuInput } from "./cpu";
import {
  runAllocation,
  validateAllocationInput,
  type AllocationAlgorithmKey,
} from "./allocation";
import { runPage, validatePageInput, type PageAlgorithmKey } from "./page";
import { runDisk, validateDiskInput, type DiskAlgorithmKey } from "./disk";
import type {
  AllocationInput,
  AlgorithmDefinition,
  CpuInput,
  DemoInput,
  DemoResult,
  DiskInput,
  PageInput,
} from "./types";

export * from "./types";
export * from "./catalog";

export function validateDemoInput(
  definition: AlgorithmDefinition,
  input: DemoInput
): string {
  if (definition.category === "cpu") {
    return validateCpuInput(
      input as CpuInput,
      definition.requiresPriority,
      definition.usesQuantum
    );
  }
  if (definition.category === "allocation") {
    return validateAllocationInput(input as AllocationInput);
  }
  if (definition.category === "page") {
    return validatePageInput(input as PageInput);
  }
  return validateDiskInput(input as DiskInput);
}

export function runDemo(
  definition: AlgorithmDefinition,
  input: DemoInput
): DemoResult {
  if (definition.category === "cpu") {
    const runner = CPU_ALGORITHMS[definition.key];
    if (!runner) throw new Error(`未接入算法：${definition.key}`);
    return runner.run(input as CpuInput);
  }
  if (definition.category === "allocation") {
    return runAllocation(
      definition.key as AllocationAlgorithmKey,
      input as AllocationInput
    );
  }
  if (definition.category === "page") {
    return runPage(definition.key as PageAlgorithmKey, input as PageInput);
  }
  return runDisk(definition.key as DiskAlgorithmKey, input as DiskInput);
}
