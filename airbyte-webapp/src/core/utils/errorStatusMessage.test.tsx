import { generateMessageFromError, FormError } from "./errorStatusMessage";

const formatMessage = () => "";

describe("#generateMessageFromError", () => {
  it("should return a provided error message", () => {
    const errMsg = "test";
    expect(generateMessageFromError(new Error(errMsg), formatMessage)).toBe(errMsg);
  });

  it("should return null if no error message and no status, or status is 0", () => {
    expect(generateMessageFromError(new Error(), formatMessage)).toBe(null);
    const fakeStatusError = new FormError();
    fakeStatusError.status = 0;
    expect(generateMessageFromError(fakeStatusError, formatMessage)).toBe(null);
  });

  it("should return a validation error message if status is 400", () => {
    const fakeStatusError = new FormError();
    fakeStatusError.status = 400;
    expect(generateMessageFromError(fakeStatusError, formatMessage)).toMatchInlineSnapshot(`
      <Memo(MemoizedFormattedMessage)
        id="form.validationError"
      />
    `);
  });

  it("should return a 'some error' message if status is > 0 and not 400", () => {
    const fakeStatusError = new FormError();
    fakeStatusError.status = 401;
    expect(generateMessageFromError(fakeStatusError, formatMessage)).toMatchInlineSnapshot(`
      <Memo(MemoizedFormattedMessage)
        id="form.someError"
      />
    `);
  });
});
